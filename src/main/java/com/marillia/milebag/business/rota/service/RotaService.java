package com.marillia.milebag.business.rota.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.pedagio.model.PracaLocalizada;
import com.marillia.milebag.business.pedagio.service.PracaPedagioService;
import com.marillia.milebag.business.rota.model.CalcularRotaRequest;
import com.marillia.milebag.business.rota.model.CalcularRotaResponse;
import com.marillia.milebag.business.rota.model.PropostaRota;
import com.marillia.milebag.business.rota.model.SelecionarRotaRequest;
import com.marillia.milebag.business.rota.model.SelecionarRotaResponse;
import com.marillia.milebag.business.rota.repository.PropostaRotaRepository;
import com.marillia.milebag.business.viagem.service.SelecaoRotaProvider;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.EnderecoNaoGeocodificavelException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.GeocodingClient;
import com.marillia.milebag.core.geo.GeometriaRota;
import com.marillia.milebag.core.geo.PracaNaRota;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.RoutingClient;
import com.marillia.milebag.core.geo.TipoSelecao;
import com.marillia.milebag.core.geo.TracadoAlternativa;

/**
 * Orquestra o calculo e a selecao de rota (US1 desta HU) - ver
 * contracts/rota-api.md. Implementa {@link SelecaoRotaProvider} (porta
 * definida por {@code viagem}, Dependency Inversion) para que {@code
 * ViagemService} leia a rota selecionada sem {@code viagem} depender deste
 * pacote - a unica dependencia direta entre os dois fica nesta classe
 * (rota -> viagem), nunca no sentido inverso.
 */
@Service
public class RotaService implements SelecaoRotaProvider {

    private static final Logger log = LoggerFactory.getLogger(RotaService.class);
    private static final double RAIO_TERRA_METROS = 6_371_000;

    private final EntregaService entregaService;
    private final BdoService bdoService;
    private final PropostaRotaRepository propostaRotaRepository;
    private final GeocodingClient geocodingClient;
    private final RoutingClient routingClient;
    private final ViagemService viagemService;
    private final ObjectMapper objectMapper;
    private final PracaPedagioService pracaPedagioService;
    /** Desvio maximo do traçado desenhado em relacao ao do motor de rotas: invisivel no zoom de cidade, e corta a maior parte dos pontos. */
    private static final double TOLERANCIA_TRACADO_MAPA_METROS = 5.0;

    private final double raioDeteccaoPedagioMetros;

    public RotaService(
            EntregaService entregaService,
            BdoService bdoService,
            PropostaRotaRepository propostaRotaRepository,
            GeocodingClient geocodingClient,
            RoutingClient routingClient,
            ViagemService viagemService,
            ObjectMapper objectMapper,
            PracaPedagioService pracaPedagioService,
            @Value("${milebag.pedagio.raio-deteccao-metros:150}") double raioDeteccaoPedagioMetros) {
        this.pracaPedagioService = pracaPedagioService;
        this.raioDeteccaoPedagioMetros = raioDeteccaoPedagioMetros;
        this.entregaService = entregaService;
        this.bdoService = bdoService;
        this.propostaRotaRepository = propostaRotaRepository;
        this.geocodingClient = geocodingClient;
        this.routingClient = routingClient;
        this.viagemService = viagemService;
        this.objectMapper = objectMapper;
    }

    /** Implementacao de {@link SelecaoRotaProvider} - consumida por {@code ViagemService.iniciarViagem}. */
    @Override
    @Transactional(readOnly = true)
    public Optional<RotaSelecionada> buscarSelecionada(UUID entregaId) {
        return propostaRotaRepository.findById(entregaId)
                .filter(p -> p.getTipoSelecao() != null && p.getRotaEscolhida() != null)
                .map(p -> new RotaSelecionada(p.getTipoSelecao(), lerRotaEscolhida(p.getRotaEscolhida())));
    }

    /** HU31: destino da rota calculada para a entrega, mostrado no link de acompanhamento - vazio se nao houver. */
    @Transactional(readOnly = true)
    public Optional<Coordenada> buscarDestino(UUID entregaId) {
        return propostaRotaRepository.findById(entregaId)
                .map(PropostaRota::getDestino)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, Coordenada.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Falha ao ler o destino persistido.", e);
                    }
                });
    }

    @Transactional
    public CalcularRotaResponse calcular(UUID entregaId, CalcularRotaRequest request) {
        if (request == null || request.origemAtual() == null) {
            throw new RegraNegocioException("A posicao atual do motorista (origemAtual) e obrigatoria.");
        }

        propostaRotaRepository.travarPorEntrega(entregaId);
        Entrega entrega = buscarEntregaOuFalhar(entregaId);
        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());

        Coordenada destino = request.destinoManual() != null
                ? request.destinoManual()
                : geocodingClient.geocodificar(bdo.getEnderecoEntrega())
                        .orElseThrow(() -> new EnderecoNaoGeocodificavelException(
                                "Nao foi possivel geocodificar o endereco de entrega; informe uma coordenada manual."));

        Coordenada origem = request.origemAtual();
        List<PracaLocalizada> pracasLocalizadas = pracaPedagioService.listarLocalizadas(LocalDate.now());
        RoutingClient.ResultadoRotas resultado = routingClient.calcularAlternativasComTracado(origem, destino);
        List<RotaAlternativa> alternativas = resultado.alternativas().stream()
                .map(rota -> rota.alternativa().comPracas(pracasNoTracado(rota.tracado(), pracasLocalizadas)))
                .toList();

        // Substitui a proposta anterior (Edge Case do spec.md): cada novo
        // calculo limpa qualquer selecao ja feita, ate a viagem comecar.
        PropostaRota proposta = propostaRotaRepository.findById(entregaId).orElseGet(PropostaRota::new);
        proposta.setEntregaId(entregaId);
        proposta.setOrigem(escrever(origem));
        proposta.setDestino(escrever(destino));
        proposta.setAlternativas(escrever(alternativas));
        proposta.setTipoSelecao(null);
        proposta.setJustificativa(null);
        proposta.setRotaEscolhida(null);
        propostaRotaRepository.save(proposta);

        log.info("Rota calculada para a entrega {} ({} alternativa(s))", entregaId, alternativas.size());
        List<TracadoAlternativa> tracados = resultado.alternativas().stream()
                .map(rota -> new TracadoAlternativa(rota.alternativa().id(),
                        GeometriaRota.simplificar(rota.tracado(), TOLERANCIA_TRACADO_MAPA_METROS)))
                .toList();
        return new CalcularRotaResponse(entregaId, origem, destino, alternativas, resultado.motorDisponivel(), tracados);
    }

    @Transactional
    public SelecionarRotaResponse selecionar(UUID entregaId, SelecionarRotaRequest request) {
        propostaRotaRepository.travarPorEntrega(entregaId);
        buscarEntregaOuFalhar(entregaId);
        PropostaRota proposta = propostaRotaRepository.findById(entregaId).orElseGet(() -> {
            // Rota propria sem um /calcular previo (ex.: sincronizacao offline,
            // HU08 - nunca houve rede para chamar Valhalla/Nominatim antes da
            // selecao): o tracado manual/justificativa abaixo nao dependem de
            // uma PropostaRota preexistente, entao criamos uma vazia em vez de
            // recusar. Selecao sugerida continua exigindo o calculo previo -
            // nao ha alternativas para escolher sem ele.
            if (request.tipoSelecao() != TipoSelecao.PROPRIA) {
                throw new RecursoNaoEncontradoException("Nenhuma proposta de rota calculada para esta entrega.");
            }
            PropostaRota novaProposta = new PropostaRota();
            novaProposta.setEntregaId(entregaId);
            novaProposta.setOrigem(escrever(null));
            novaProposta.setDestino(escrever(null));
            novaProposta.setAlternativas(escrever(List.of()));
            return novaProposta;
        });

        RotaAlternativa rotaEscolhida = request.tipoSelecao() == TipoSelecao.PROPRIA
                ? construirRotaPropria(request)
                : localizarAlternativa(proposta, request.alternativaId());

        // T013a: com o trajeto ja em execucao, a selecao vira um novo
        // CalculoRota no historico (HU04) em vez de so atualizar a proposta
        // corrente; ViagemService recusa com 409 se o trajeto ja estiver
        // encerrado (contracts/rota-api.md).
        boolean viagemEmExecucao = viagemService.registrarCalculoRotaSeTrajetoExistir(entregaId, rotaEscolhida, request.tipoSelecao());
        if (!viagemEmExecucao) {
            proposta.setTipoSelecao(request.tipoSelecao());
            proposta.setJustificativa(request.justificativa());
        }
        proposta.setRotaEscolhida(escrever(rotaEscolhida));
        propostaRotaRepository.save(proposta);

        log.info("Rota selecionada ({}) para a entrega {}", request.tipoSelecao(), entregaId);
        return new SelecionarRotaResponse(entregaId, request.tipoSelecao(), rotaEscolhida, request.justificativa());
    }

    private RotaAlternativa construirRotaPropria(SelecionarRotaRequest request) {
        if (request.justificativa() == null || request.justificativa().isBlank()) {
            throw new RegraNegocioException("Justificativa e obrigatoria para rota propria.");
        }
        // Sem validacao automatica de plausibilidade nesta fase (Assumptions
        // do spec.md); distancia estimada por linha reta entre os pontos do
        // tracado, ja que nenhum motor de rotas e chamado sobre um trajeto
        // desenhado manualmente. Duracao fica indefinida (0).
        double distanciaMetros = calcularDistanciaTracado(request.tracadoManual());
        // HU24: o tracado desenhado pelo motorista tambem passa (ou nao) por pracas cadastradas.
        List<PracaNaRota> pracas = pracasNoTracado(request.tracadoManual(), pracaPedagioService.listarLocalizadas(LocalDate.now()));
        return new RotaAlternativa("propria", distanciaMetros, 0, false).comPracas(pracas);
    }

    /** Pracas cadastradas (HU10) a no maximo o raio de deteccao do tracado, com o valor vigente por passagem (so para exibir). */
    private List<PracaNaRota> pracasNoTracado(List<Coordenada> tracado, List<PracaLocalizada> pracas) {
        return GeometriaRota.proximosDoTracado(tracado, pracas, PracaLocalizada::localizacao, raioDeteccaoPedagioMetros).stream()
                .map(praca -> new PracaNaRota(praca.id(), praca.nome(), praca.valorVigente()))
                .toList();
    }

    private RotaAlternativa localizarAlternativa(PropostaRota proposta, String alternativaId) {
        if (alternativaId == null) {
            throw new RegraNegocioException("alternativaId e obrigatorio para selecao sugerida.");
        }
        return ler(proposta.getAlternativas()).stream()
                .filter(alternativa -> alternativa.id().equals(alternativaId))
                .findFirst()
                .orElseThrow(() -> new RegraNegocioException("Alternativa de rota informada nao existe."));
    }

    private double calcularDistanciaTracado(List<Coordenada> tracado) {
        if (tracado == null || tracado.size() < 2) {
            return 0;
        }
        double totalMetros = 0;
        for (int i = 1; i < tracado.size(); i++) {
            totalMetros += distanciaHaversine(tracado.get(i - 1), tracado.get(i));
        }
        return totalMetros;
    }

    private double distanciaHaversine(Coordenada a, Coordenada b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return RAIO_TERRA_METROS * c;
    }

    private Bdo buscarBdoOuFalhar(UUID bdoId) {
        return bdoService.buscarPorId(bdoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO da entrega nao encontrado."));
    }

    private Entrega buscarEntregaOuFalhar(UUID entregaId) {
        return entregaService.buscarPorId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada, ou ainda nao autoatribuida."));
    }

    private String escrever(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar dados da proposta de rota.", e);
        }
    }

    private List<RotaAlternativa> ler(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<RotaAlternativa>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler as alternativas de rota persistidas.", e);
        }
    }

    private RotaAlternativa lerRotaEscolhida(String json) {
        try {
            return objectMapper.readValue(json, RotaAlternativa.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler a rota escolhida persistida.", e);
        }
    }
}
