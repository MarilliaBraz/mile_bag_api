package com.marillia.milebag.business.entrega.bagagemcarona;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.GeocodingClient;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.RoutingClient;

/**
 * Vincula/desvincula bagagens carona a uma entrega principal em andamento
 * (US1 desta HU) - ver contracts/bagagem-carona-api.md.
 */
@Service
public class BagagemCaronaService {

    private static final Logger log = LoggerFactory.getLogger(BagagemCaronaService.class);

    private final BagagemCaronaRepository bagagemCaronaRepository;
    private final EntregaRepository entregaRepository;
    private final BdoService bdoService;
    private final GeocodingClient geocodingClient;
    private final RoutingClient routingClient;

    public BagagemCaronaService(
            BagagemCaronaRepository bagagemCaronaRepository,
            EntregaRepository entregaRepository,
            BdoService bdoService,
            GeocodingClient geocodingClient,
            RoutingClient routingClient) {
        this.bagagemCaronaRepository = bagagemCaronaRepository;
        this.entregaRepository = entregaRepository;
        this.bdoService = bdoService;
        this.geocodingClient = geocodingClient;
        this.routingClient = routingClient;
    }

    @Transactional
    public BagagemCarona vincular(UUID entregaPrincipalId, UUID bdoId) {
        Entrega entregaPrincipal = buscarEntregaOuFalhar(entregaPrincipalId);
        if (entregaPrincipal.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("A entrega principal ja foi encerrada com comprovante.");
        }
        EntregaService.exigirNaoCancelada(entregaPrincipal);

        Bdo bdoCarona = buscarBdoOuFalhar(bdoId);
        // O BDO de carona nao pode ter entrega propria (nem ser o da entrega principal): senao seria entregue duas vezes.
        if (entregaRepository.findByBdoId(bdoId).isPresent()) {
            throw new ConflitoException("Este BDO ja tem entrega propria; nao pode ser bagagem carona.");
        }
        if (bagagemCaronaRepository.findByBdoId(bdoId).isPresent()) {
            throw new ConflitoException("Este BDO ja esta vinculado a outra bagagem carona.");
        }

        Bdo bdoPrincipal = buscarBdoOuFalhar(entregaPrincipal.getBdoId());
        // V52: a carona sai do mesmo aeroporto da principal.
        if (bdoCarona.getBaseId() != null && bdoPrincipal.getBaseId() != null && !bdoCarona.getBaseId().equals(bdoPrincipal.getBaseId())) {
            throw new ConflitoException("A bagagem carona precisa ser da mesma base da entrega principal.");
        }
        BigDecimal quilometragemComplementar = calcularQuilometragemComplementar(bdoPrincipal, bdoCarona);

        BagagemCarona bagagemCarona =
                bagagemCaronaRepository.save(new BagagemCarona(bdoId, entregaPrincipalId, quilometragemComplementar));

        log.info(
                "Bagagem carona {} (BDO {}) vinculada a entrega {} ({} km complementares)",
                bagagemCarona.getId(), bdoId, entregaPrincipalId, quilometragemComplementar);
        return bagagemCarona;
    }

    @Transactional
    public void desvincular(UUID entregaPrincipalId, UUID bagagemCaronaId) {
        BagagemCarona bagagemCarona = bagagemCaronaRepository.findById(bagagemCaronaId)
                .filter(bc -> bc.getEntregaPrincipalId().equals(entregaPrincipalId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vinculo de bagagem carona nao encontrado."));

        Entrega entregaPrincipal = buscarEntregaOuFalhar(entregaPrincipalId);
        if (entregaPrincipal.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("A entrega principal ja foi encerrada com comprovante; nao e possivel remover o vinculo.");
        }

        bagagemCaronaRepository.delete(bagagemCarona);
        log.info("Vinculo de bagagem carona {} removido da entrega {}", bagagemCaronaId, entregaPrincipalId);
    }

    /**
     * V47, cancelamento do BDO pela empresa: o BDO cancelado deixa de ser carona de outra entrega, e as caronas da
     * entrega dele (se tinha) voltam a ficar disponiveis para os motoristas. O encaminhamento da companhia aerea de
     * cada carona solta volta a "aguardando motorista" (trigger da V47); so o do BDO cancelado fica CANCELADO.
     */
    @Transactional
    public void soltarDoCancelamento(UUID bdoCanceladoId, UUID entregaCanceladaId) {
        bagagemCaronaRepository.findByBdoId(bdoCanceladoId).ifPresent(bagagemCaronaRepository::delete);
        if (entregaCanceladaId != null) {
            bagagemCaronaRepository.deleteAll(bagagemCaronaRepository.findByEntregaPrincipalId(entregaCanceladaId));
        }
        bagagemCaronaRepository.flush();
    }

    /** V47: a entrega com que este BDO viaja como carona, se viaja. */
    @Transactional(readOnly = true)
    public Optional<Entrega> entregaPrincipalDe(UUID bdoId) {
        return bagagemCaronaRepository.findByBdoId(bdoId).flatMap(carona -> entregaRepository.findById(carona.getEntregaPrincipalId()));
    }

    /** Tela "Entregas" da empresa (V47): quais destes BDOs viajam como carona de outra entrega - numa consulta so. */
    @Transactional(readOnly = true)
    public Set<UUID> bdosQueSaoCarona(Collection<UUID> bdoIds) {
        if (bdoIds.isEmpty()) {
            return Set.of();
        }
        return bagagemCaronaRepository.findByBdoIdIn(bdoIds).stream().map(BagagemCarona::getBdoId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<BagagemCarona> listarPorEntrega(UUID entregaPrincipalId) {
        buscarEntregaOuFalhar(entregaPrincipalId);
        return bagagemCaronaRepository.findByEntregaPrincipalId(entregaPrincipalId);
    }

    /**
     * Trecho adicional de deslocamento necessario para incluir o endereco da
     * bagagem carona na rota ja em execucao (research.md desta HU): a
     * distancia do destino da entrega principal ate o destino da bagagem
     * carona. Falha de geocodificacao ou do motor de rotas nao bloqueia a
     * vinculacao (contrato desta HU so define 404/409) - fica registrada
     * como 0 e sinalizada em log para conferencia manual.
     */
    private BigDecimal calcularQuilometragemComplementar(Bdo bdoPrincipal, Bdo bdoCarona) {
        Optional<Coordenada> destinoPrincipal = geocodingClient.geocodificar(bdoPrincipal.getEnderecoEntrega());
        Optional<Coordenada> destinoCarona = geocodingClient.geocodificar(bdoCarona.getEnderecoEntrega());
        if (destinoPrincipal.isEmpty() || destinoCarona.isEmpty()) {
            log.warn("Nao foi possivel geocodificar os enderecos para calcular a quilometragem complementar da bagagem carona - usando 0.");
            return BigDecimal.ZERO;
        }

        List<RotaAlternativa> alternativas = routingClient.calcularAlternativas(destinoPrincipal.get(), destinoCarona.get());
        if (alternativas.isEmpty()) {
            log.warn("Motor de rotas nao retornou alternativas para a quilometragem complementar da bagagem carona - usando 0.");
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(alternativas.get(0).distanciaMetros() / 1000.0).setScale(3, RoundingMode.HALF_UP);
    }

    private Entrega buscarEntregaOuFalhar(UUID entregaId) {
        return entregaRepository.findById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega principal nao encontrada."));
    }

    private Bdo buscarBdoOuFalhar(UUID bdoId) {
        return bdoService.buscarPorId(bdoId).orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
    }
}
