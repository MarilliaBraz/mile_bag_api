package com.marillia.milebag.business.viagem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.business.rota.model.PropostaRota;
import com.marillia.milebag.business.rota.model.RotaAlternativa;
import com.marillia.milebag.business.rota.model.TipoSelecao;
import com.marillia.milebag.business.rota.repository.PropostaRotaRepository;
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

/**
 * Registra o trajeto real da viagem - inicio, recalculos (acionados por
 * {@code RotaService}, HU03), checkpoints de GPS e consolidacao no
 * encerramento (RN14). Ver contracts/viagem-api.md.
 */
@Service
public class ViagemService {

    private static final Logger log = LoggerFactory.getLogger(ViagemService.class);

    // HU11 (limiar de divergencia configuravel por tenant) ainda nao existe;
    // fallback para o padrao do sistema, conforme tasks.md/research.md desta HU.
    private static final BigDecimal DIVERGENCIA_PERCENTUAL_PADRAO = new BigDecimal("0.20");
    private static final BigDecimal DIVERGENCIA_KM_MINIMO_PADRAO = new BigDecimal("3");

    private final EntregaRepository entregaRepository;
    private final PropostaRotaRepository propostaRotaRepository;
    private final TrajetoRepository trajetoRepository;
    private final CalculoRotaRepository calculoRotaRepository;
    private final CheckpointGpsRepository checkpointGpsRepository;
    private final TrajetoMapper trajetoMapper;
    private final ObjectMapper objectMapper;

    public ViagemService(
            EntregaRepository entregaRepository,
            PropostaRotaRepository propostaRotaRepository,
            TrajetoRepository trajetoRepository,
            CalculoRotaRepository calculoRotaRepository,
            CheckpointGpsRepository checkpointGpsRepository,
            TrajetoMapper trajetoMapper,
            ObjectMapper objectMapper) {
        this.entregaRepository = entregaRepository;
        this.propostaRotaRepository = propostaRotaRepository;
        this.trajetoRepository = trajetoRepository;
        this.calculoRotaRepository = calculoRotaRepository;
        this.checkpointGpsRepository = checkpointGpsRepository;
        this.trajetoMapper = trajetoMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TrajetoIniciadoResponse iniciarViagem(UUID entregaId, IniciarViagemRequest request) {
        Entrega entrega = buscarEntregaOuFalhar(entregaId);

        if (trajetoRepository.findByEntregaId(entregaId).isPresent()) {
            throw new ConflitoException("Esta entrega ja possui um trajeto iniciado.");
        }

        PropostaRota proposta = propostaRotaRepository.findById(entregaId)
                .filter(p -> p.getTipoSelecao() != null && p.getRotaEscolhida() != null)
                .orElseThrow(() -> new RotaNaoSelecionadaException(
                        "Nenhuma rota foi selecionada para esta entrega; selecione uma rota antes de iniciar a viagem."));

        RotaAlternativa rotaEscolhida = ler(proposta.getRotaEscolhida());
        Instant agora = Instant.now();

        Trajeto trajeto = trajetoRepository.save(new Trajeto(entregaId, agora));

        CalculoRota calculoInicial = calculoRotaRepository.save(
                criarCalculoRota(trajeto.getId(), rotaEscolhida, proposta.getTipoSelecao(), agora, true));

        boolean semCoordenadas = request == null || request.lat() == null || request.lng() == null;
        CheckpointGps checkpointPartida = checkpointGpsRepository.save(new CheckpointGps(
                trajeto.getId(),
                TipoCheckpoint.PARTIDA,
                semCoordenadas ? null : request.lat(),
                semCoordenadas ? null : request.lng(),
                agora,
                semCoordenadas));

        entrega.setStatus(EntregaStatus.EM_ANDAMENTO);
        entregaRepository.save(entrega);

        log.info("Viagem iniciada: trajeto {} para a entrega {}", trajeto.getId(), entregaId);
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

        boolean semCoordenadas = request.lat() == null || request.lng() == null;
        CheckpointGps checkpoint = checkpointGpsRepository.save(new CheckpointGps(
                trajeto.getId(),
                request.tipo(),
                semCoordenadas ? null : request.lat(),
                semCoordenadas ? null : request.lng(),
                Instant.now(),
                semCoordenadas));

        log.info("Checkpoint {} registrado no trajeto {}", request.tipo(), trajeto.getId());
        return trajetoMapper.toResponse(checkpoint);
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
                escrever(construirPracasPedagio(alternativa.possuiPedagio())),
                alternativa.duracaoSegundos(),
                tipoSelecao,
                dataHora,
                ehRotaInicial);
    }

    private List<TrechoPedagio> construirPracasPedagio(boolean possuiPedagio) {
        return possuiPedagio
                ? List.of(new TrechoPedagio("Pedagio identificado pelo motor de rotas (sem cadastro de pracas ainda)", false))
                : List.of();
    }

    private Entrega buscarEntregaOuFalhar(UUID entregaId) {
        return entregaRepository.findById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
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

    private RotaAlternativa ler(String json) {
        try {
            return objectMapper.readValue(json, RotaAlternativa.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler a rota escolhida persistida.", e);
        }
    }
}
