package com.marillia.milebag.business.entrega.bagagemcarona;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.business.rota.model.Coordenada;
import com.marillia.milebag.business.rota.model.RotaAlternativa;
import com.marillia.milebag.business.rota.service.GeocodingClient;
import com.marillia.milebag.business.rota.service.RoutingClient;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Vincula/desvincula bagagens carona a uma entrega principal em andamento
 * (US1 desta HU) - ver contracts/bagagem-carona-api.md.
 */
@Service
public class BagagemCaronaService {

    private static final Logger log = LoggerFactory.getLogger(BagagemCaronaService.class);

    private final BagagemCaronaRepository bagagemCaronaRepository;
    private final EntregaRepository entregaRepository;
    private final BdoRepository bdoRepository;
    private final GeocodingClient geocodingClient;
    private final RoutingClient routingClient;

    public BagagemCaronaService(
            BagagemCaronaRepository bagagemCaronaRepository,
            EntregaRepository entregaRepository,
            BdoRepository bdoRepository,
            GeocodingClient geocodingClient,
            RoutingClient routingClient) {
        this.bagagemCaronaRepository = bagagemCaronaRepository;
        this.entregaRepository = entregaRepository;
        this.bdoRepository = bdoRepository;
        this.geocodingClient = geocodingClient;
        this.routingClient = routingClient;
    }

    @Transactional
    public BagagemCarona vincular(UUID entregaPrincipalId, UUID bdoId) {
        Entrega entregaPrincipal = buscarEntregaOuFalhar(entregaPrincipalId);
        if (entregaPrincipal.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("A entrega principal ja foi encerrada com comprovante.");
        }

        Bdo bdoCarona = buscarBdoOuFalhar(bdoId);
        if (bagagemCaronaRepository.findByBdoId(bdoId).isPresent()) {
            throw new ConflitoException("Este BDO ja esta vinculado a outra bagagem carona.");
        }

        Bdo bdoPrincipal = buscarBdoOuFalhar(entregaPrincipal.getBdoId());
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
        return bdoRepository.findById(bdoId).orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
    }
}
