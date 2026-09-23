package com.marillia.milebag.business.entrega.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.mapper.PainelEntregasMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.ItemPainelDetalheResponse;
import com.marillia.milebag.business.entrega.model.ItemPainelResponse;
import com.marillia.milebag.business.entrega.model.PainelEntregasResponse;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Painel de entregas pendentes do motorista (HU19, US1) - ver
 * contracts/painel-api.md. Read model agregado sobre Entrega/BDO/
 * BagagemCarona/Trajeto ja existentes - sem persistencia propria
 * (CONSTITUTION.md 2.3, simplicidade).
 */
@Service
public class PainelEntregasService {

    private static final Logger log = LoggerFactory.getLogger(PainelEntregasService.class);

    private static final List<EntregaStatus> STATUS_PENDENTES =
            List.of(EntregaStatus.ATRIBUIDA, EntregaStatus.EM_ANDAMENTO, EntregaStatus.REABERTA);

    private final EntregaRepository entregaRepository;
    private final BdoService bdoService;
    private final BagagemCaronaService bagagemCaronaService;
    private final PainelEntregasMapper mapper;

    public PainelEntregasService(
            EntregaRepository entregaRepository,
            BdoService bdoService,
            BagagemCaronaService bagagemCaronaService,
            PainelEntregasMapper mapper) {
        this.entregaRepository = entregaRepository;
        this.bdoService = bdoService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PainelEntregasResponse consultarPainel(UUID motoristaId) {
        List<ItemPainelResponse> itens = entregaRepository
                .findByMotoristaResponsavelAndStatusInOrderByAtualizadoEmAsc(motoristaId, STATUS_PENDENTES)
                .stream()
                .map(this::montarItem)
                .toList();

        log.info("Painel consultado: motorista {} tem {} entrega(s) pendente(s)", motoristaId, itens.size());
        return new PainelEntregasResponse(itens.size(), itens);
    }

    /** 403 (nao pertence ao motorista) e distinto de 404 (RLS ja resolve "nao existe"/"outro tenant" como vazio, FR-003/contracts). */
    @Transactional(readOnly = true)
    public ItemPainelDetalheResponse consultarDetalhe(UUID entregaId, UUID motoristaId) {
        Entrega entrega = buscarEntregaOuFalhar(entregaId);
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new AcessoNegadoException("Esta entrega nao pertence ao motorista autenticado.");
        }

        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
        List<BagagemCarona> bagagensCarona = bagagemCaronaService.listarPorEntrega(entregaId);
        return mapper.toDetalhe(entrega, bdo, bagagensCarona, this::etiquetaDoBdo);
    }

    private ItemPainelResponse montarItem(Entrega entrega) {
        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
        List<BagagemCarona> bagagensCarona = bagagemCaronaService.listarPorEntrega(entrega.getId());
        return mapper.toItemPainel(entrega, bdo, bagagensCarona, this::etiquetaDoBdo);
    }

    /** Bagagem carona referencia o proprio BDO (nao o da entrega principal) para a etiqueta exibida no resumo. */
    private String etiquetaDoBdo(UUID bdoId) {
        return bdoService.buscarPorId(bdoId).map(Bdo::getEtiquetaBagagem).orElse(null);
    }

    private Entrega buscarEntregaOuFalhar(UUID entregaId) {
        return entregaRepository.findById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
    }

    private Bdo buscarBdoOuFalhar(UUID bdoId) {
        return bdoService.buscarPorId(bdoId)
                .orElseThrow(() -> new IllegalStateException("Entrega referencia um BDO inexistente: " + bdoId));
    }
}
