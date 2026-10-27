package com.marillia.milebag.business.viagem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.pedagio.service.PracaPedagioService;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.viagem.mapper.TrajetoMapper;
import com.marillia.milebag.business.viagem.model.CalculoRota;
import com.marillia.milebag.business.viagem.model.CheckpointGps;
import com.marillia.milebag.business.viagem.model.CheckpointResponse;
import com.marillia.milebag.business.viagem.model.EncerrarTrajetoRequest;
import com.marillia.milebag.business.viagem.model.IniciarViagemRequest;
import com.marillia.milebag.business.viagem.model.RegistrarCheckpointRequest;
import com.marillia.milebag.business.viagem.model.TipoCheckpoint;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrajetoDetalhadoResponse;
import com.marillia.milebag.business.viagem.model.TrajetoIniciadoResponse;
import com.marillia.milebag.business.viagem.model.TrajetoStatus;
import com.marillia.milebag.business.viagem.model.TrechoPedagio;
import com.marillia.milebag.business.viagem.repository.CalculoRotaRepository;
import com.marillia.milebag.business.viagem.repository.CheckpointGpsRepository;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.DivergenciaSemJustificativaException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RotaNaoSelecionadaException;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.TipoSelecao;

/**
 * Registra o trajeto real da viagem - inicio, recalculos (acionados por
 * {@code RotaService}, HU03), checkpoints de GPS e consolidacao no
 * encerramento (RN14). Ver contracts/viagem-api.md.
 *
 * <p>Le a rota selecionada via {@link SelecaoRotaProvider} (implementada por
 * {@code RotaService}), nunca de {@code PropostaRotaRepository} direto -
 * essa porta e definida aqui (nao em {@code business.rota}) exatamente para
 * este pacote nao precisar importar nada de {@code business.rota}, o que
 * criaria uma dependencia circular (rota ja depende de viagem para
 * {@link #registrarCalculoRotaSeTrajetoExistir}).</p>
 */
@Service
public class ViagemService {

    private static final Logger log = LoggerFactory.getLogger(ViagemService.class);

    // HU11 (limiar de divergencia configuravel por tenant) ainda nao existe;
    // fallback para o padrao do sistema, conforme tasks.md/research.md desta HU.
    private static final BigDecimal DIVERGENCIA_PERCENTUAL_PADRAO = new BigDecimal("0.20");
    private static final BigDecimal DIVERGENCIA_KM_MINIMO_PADRAO = new BigDecimal("3");

    // HU20, FR-006: unicos tipos de CheckpointGps que contam como "marco de
    // progresso" no painel - PARTIDA/COMPROVANTE/OCORRENCIA nao sao marcos.
    private static final List<TipoCheckpoint> TIPOS_MARCO_PROGRESSO = List.of(TipoCheckpoint.A_CAMINHO, TipoCheckpoint.CHEGADA);

    private final EntregaService entregaService;
    private final SelecaoRotaProvider selecaoRotaProvider;
    private final TrajetoRepository trajetoRepository;
    private final CalculoRotaRepository calculoRotaRepository;
    private final CheckpointGpsRepository checkpointGpsRepository;
    private final TrajetoMapper trajetoMapper;
    private final ObjectMapper objectMapper;
    private final MensagemService mensagemService;
    private final PracaPedagioService pracaPedagioService;

    // @Lazy: a inversao por SelecaoRotaProvider tira a dependencia de PACOTE
    // (viagem nao importa nada de rota), mas em runtime o Spring ainda ve o
    // ciclo de beans viagem -> RotaService -> viagem (RotaService registra
    // recalculos via registrarCalculoRotaSeTrajetoExistir). O proxy adia a
    // resolucao do RotaService para o primeiro uso, depois da inicializacao.
    public ViagemService(
            EntregaService entregaService,
            @Lazy SelecaoRotaProvider selecaoRotaProvider,
            TrajetoRepository trajetoRepository,
            CalculoRotaRepository calculoRotaRepository,
            CheckpointGpsRepository checkpointGpsRepository,
            TrajetoMapper trajetoMapper,
            ObjectMapper objectMapper,
            MensagemService mensagemService,
            PracaPedagioService pracaPedagioService) {
        this.pracaPedagioService = pracaPedagioService;
        this.entregaService = entregaService;
        this.selecaoRotaProvider = selecaoRotaProvider;
        this.trajetoRepository = trajetoRepository;
        this.calculoRotaRepository = calculoRotaRepository;
        this.checkpointGpsRepository = checkpointGpsRepository;
        this.trajetoMapper = trajetoMapper;
        this.objectMapper = objectMapper;
        this.mensagemService = mensagemService;
    }

    /** Ponto de leitura publico para outros dominios (fechamento) - nunca {@code TrajetoRepository} direto (CONVENTIONS.md 2.1). */
    @Transactional(readOnly = true)
    public Optional<Trajeto> buscarTrajetoPorEntrega(UUID entregaId) {
        return trajetoRepository.findByEntregaId(entregaId);
    }

    @Transactional
    public TrajetoIniciadoResponse iniciarViagem(UUID entregaId, IniciarViagemRequest request) {
        if (entregaService.buscarPorId(entregaId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Entrega nao encontrada.");
        }

        if (trajetoRepository.findByEntregaId(entregaId).isPresent()) {
            throw new ConflitoException("Esta entrega ja possui um trajeto iniciado.");
        }

        SelecaoRotaProvider.RotaSelecionada selecionada = selecaoRotaProvider.buscarSelecionada(entregaId)
                .orElseThrow(() -> new RotaNaoSelecionadaException(
                        "Nenhuma rota foi selecionada para esta entrega; selecione uma rota antes de iniciar a viagem."));

        Instant agora = Instant.now();

        Trajeto trajeto = trajetoRepository.save(new Trajeto(entregaId, agora));

        CalculoRota calculoInicial = calculoRotaRepository.save(
                criarCalculoRota(trajeto.getId(), selecionada.rotaEscolhida(), selecionada.tipoSelecao(), agora, true));

        boolean semCoordenadas = request == null || request.lat() == null || request.lng() == null;
        CheckpointGps checkpointPartida = checkpointGpsRepository.save(new CheckpointGps(
                trajeto.getId(),
                TipoCheckpoint.PARTIDA,
                semCoordenadas ? null : request.lat(),
                semCoordenadas ? null : request.lng(),
                agora,
                semCoordenadas));

        entregaService.marcarEmAndamento(entregaId);

        log.info("Viagem iniciada: trajeto {} para a entrega {}", trajeto.getId(), entregaId);
        notificarAutomaticaComSeguranca(entregaId, TipoEventoNotificacao.VIAGEM_INICIADA);
        return trajetoMapper.toIniciadoResponse(trajeto, calculoInicial, checkpointPartida);
    }

    @Transactional(readOnly = true)
    public TrajetoDetalhadoResponse consultar(UUID entregaId) {
        Trajeto trajeto = buscarTrajetoOuFalhar(entregaId);
        return montarDetalhado(trajeto);
    }

    /**
     * Acionado por {@code RotaService.selecionar} (HU03), nunca por um
     * endpoint proprio desta HU. Retorna {@code false} quando a entrega
     * ainda nao tem trajeto (a viagem nao comecou) - nesse caso quem chamou
     * deve seguir o caminho de pre-viagem (upsert em {@code proposta_rota}).
     */
    @Transactional
    public boolean registrarCalculoRotaSeTrajetoExistir(UUID entregaId, RotaAlternativa alternativa, TipoSelecao tipoSelecao) {
        Trajeto trajeto = trajetoRepository.findByEntregaId(entregaId).orElse(null);
        if (trajeto == null) {
            return false;
        }
        if (trajeto.getStatus() == TrajetoStatus.ENCERRADO) {
            throw new ConflitoException("A viagem desta entrega ja esta encerrada; nao e possivel registrar novo calculo de rota.");
        }

        CalculoRota calculo = calculoRotaRepository.save(
                criarCalculoRota(trajeto.getId(), alternativa, tipoSelecao, Instant.now(), false));

        log.info("Recalculo de rota registrado no trajeto {} (calculo {})", trajeto.getId(), calculo.getId());
        return true;
    }

    @Transactional
    public CheckpointResponse registrarCheckpoint(UUID entregaId, RegistrarCheckpointRequest request) {
        Trajeto trajeto = buscarTrajetoOuFalhar(entregaId);
        if (trajeto.getStatus() == TrajetoStatus.ENCERRADO) {
            throw new ConflitoException("O trajeto desta entrega ja esta encerrado; nao aceita novos checkpoints.");
        }

        // HU20: repetir um marco fica no historico, mas o passageiro so e avisado na primeira vez.
        TipoEventoNotificacao evento = eventoDoMarco(request.tipo());
        boolean marcoJaRegistrado = evento != null
                && checkpointGpsRepository.existsByTrajetoIdAndTipo(trajeto.getId(), request.tipo());

        boolean semCoordenadas = request.lat() == null || request.lng() == null;
        CheckpointGps checkpoint = checkpointGpsRepository.save(new CheckpointGps(
                trajeto.getId(),
                request.tipo(),
                semCoordenadas ? null : request.lat(),
                semCoordenadas ? null : request.lng(),
                Instant.now(),
                semCoordenadas));

        log.info("Checkpoint {} registrado no trajeto {}", request.tipo(), trajeto.getId());
        if (evento != null && !marcoJaRegistrado) {
            notificarAutomaticaComSeguranca(entregaId, evento);
        }
        return trajetoMapper.toResponse(checkpoint);
    }

    /** HU18: so A_CAMINHO/CHEGADA viram evento de notificacao - PARTIDA/COMPROVANTE/OCORRENCIA nao (data-model.md desta HU). */
    private TipoEventoNotificacao eventoDoMarco(TipoCheckpoint tipo) {
        return switch (tipo) {
            case A_CAMINHO -> TipoEventoNotificacao.A_CAMINHO;
            case CHEGADA -> TipoEventoNotificacao.CHEGADA;
            default -> null;
        };
    }

    /** HU20, FR-006: o marco (a_caminho/chegada) mais recente da entrega, para o painel (HU19) - null quando nenhum foi registrado ainda. */
    @Transactional(readOnly = true)
    public Optional<String> marcoMaisRecente(UUID entregaId) {
        return trajetoRepository.findByEntregaId(entregaId)
                .flatMap(trajeto -> checkpointGpsRepository.findFirstByTrajetoIdAndTipoInOrderByDataHoraDesc(
                        trajeto.getId(), TIPOS_MARCO_PROGRESSO))
                .map(checkpoint -> checkpoint.getTipo().name());
    }

    /**
     * FR-005 (HU20): a notificacao nunca bloqueia nem falha o registro do
     * marco/inicio de viagem (contracts/marcos-api.md, HU20) - a protecao
     * fica aqui (chamador), nao dentro de {@link MensagemService}.
     */
    private void notificarAutomaticaComSeguranca(UUID entregaId, TipoEventoNotificacao tipoEvento) {
        try {
            mensagemService.notificarAutomatica(entregaId, tipoEvento);
        } catch (RuntimeException e) {
            log.warn("Falha ao notificar automaticamente a entrega {} do marco {} - checkpoint ja registrado, seguindo.", entregaId, tipoEvento, e);
        }
    }

    @Transactional
    public TrajetoDetalhadoResponse encerrarTrajeto(UUID entregaId, EncerrarTrajetoRequest request) {
        Trajeto trajeto = buscarTrajetoOuFalhar(entregaId);

        // Idempotencia (research.md desta HU): reenviar apos ja encerrado
        // devolve o resultado ja consolidado, sem reprocessar.
        if (trajeto.getStatus() == TrajetoStatus.ENCERRADO) {
            return montarDetalhado(trajeto);
        }

        List<CalculoRota> historico = calculoRotaRepository.findByTrajetoIdOrderByDataHoraAsc(trajeto.getId());
        CalculoRota calculoInicial = historico.stream()
                .filter(CalculoRota::isEhRotaInicial)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Trajeto " + trajeto.getId() + " nao tem calculo de rota inicial."));
        // O ultimo calculo ativo determina a rota final (data-model.md, "Fluxo
        // de consolidacao").
        CalculoRota calculoFinal = historico.get(historico.size() - 1);

        BigDecimal kmTotalInicial = calculoInicial.getKmIda().add(calculoInicial.getKmVolta());
        BigDecimal kmTotalFinal = calculoFinal.getKmIda().add(calculoFinal.getKmVolta());
        BigDecimal divergencia = kmTotalFinal.subtract(kmTotalInicial).abs();
        BigDecimal limiar = kmTotalInicial.multiply(DIVERGENCIA_PERCENTUAL_PADRAO).max(DIVERGENCIA_KM_MINIMO_PADRAO);

        String justificativa = request == null ? null : request.justificativaDivergencia();
        boolean divergenciaAtingida = divergencia.compareTo(limiar) >= 0;
        if (divergenciaAtingida && (justificativa == null || justificativa.isBlank())) {
            log.warn(
                    "Encerramento do trajeto {} bloqueado: divergencia de {} km >= limiar de {} km, sem justificativa.",
                    trajeto.getId(), divergencia, limiar);
            throw new DivergenciaSemJustificativaException(
                    "A quilometragem final diverge da rota inicialmente sugerida acima do limiar permitido; informe uma justificativa.");
        }

        trajeto.consolidar(
                calculoFinal.getKmIda(),
                calculoFinal.getKmVolta(),
                calculoFinal.getPracasPedagio(),
                calculoFinal.getTempoEstimado(),
                divergenciaAtingida ? justificativa : null);
        trajetoRepository.save(trajeto);

        log.info(
                "Trajeto {} encerrado (divergencia {} km, limiar {} km, justificativa {})",
                trajeto.getId(), divergencia, limiar, divergenciaAtingida ? "informada" : "nao exigida");
        return montarDetalhado(trajeto);
    }

    private TrajetoDetalhadoResponse montarDetalhado(Trajeto trajeto) {
        List<CalculoRota> calculosRota = calculoRotaRepository.findByTrajetoIdOrderByDataHoraAsc(trajeto.getId());
        List<CheckpointGps> checkpoints = checkpointGpsRepository.findByTrajetoIdOrderByDataHoraAsc(trajeto.getId());
        return trajetoMapper.toDetalhadoResponse(trajeto, calculosRota, checkpoints);
    }

    private CalculoRota criarCalculoRota(
            UUID trajetoId, RotaAlternativa alternativa, TipoSelecao tipoSelecao, Instant dataHora, boolean ehRotaInicial) {
        // RN06/RF11: pedagio e quilometragem sao sempre considerados ida e
        // volta; HU03 so calcula um trajeto (origem->destino), entao a volta
        // e assumida simetrica a ida - nao ha, ainda, um segundo calculo
        // rastreado para o trecho de retorno.
        BigDecimal km = BigDecimal.valueOf(alternativa.distanciaMetros() / 1000.0).setScale(3, RoundingMode.HALF_UP);
        return new CalculoRota(
                trajetoId,
                km,
                km,
                escrever(construirPracasPedagio(alternativa, dataHora)),
                alternativa.duracaoSegundos(),
                tipoSelecao,
                dataHora,
                ehRotaInicial);
    }

    /**
     * HU24: com pracas cadastradas no tracado, um trecho por praca com o valor vigente CONGELADO na data do calculo
     * (mudancas de valor depois disso nao alteram a viagem). Sem nenhuma, cai no indicador agregado do motor de rotas.
     */
    private List<TrechoPedagio> construirPracasPedagio(RotaAlternativa alternativa, Instant dataHora) {
        if (!alternativa.pracasPedagio().isEmpty()) {
            LocalDate data = LocalDate.ofInstant(dataHora, ZoneId.systemDefault());
            return alternativa.pracasPedagio().stream()
                    .map(praca -> {
                        Optional<BigDecimal> valor = pracaPedagioService.buscarValorVigenteEm(praca.pracaId(), data);
                        return new TrechoPedagio(praca.nome(), valor.isPresent(), praca.pracaId(), valor.orElse(null));
                    })
                    .toList();
        }
        return alternativa.possuiPedagio()
                ? List.of(new TrechoPedagio("Pedagio identificado pelo motor de rotas (nenhuma praca cadastrada no tracado)", false))
                : List.of();
    }

    private Trajeto buscarTrajetoOuFalhar(UUID entregaId) {
        return trajetoRepository.findByEntregaId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esta entrega nao tem um trajeto iniciado."));
    }

    private String escrever(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar dados do trajeto.", e);
        }
    }
}
