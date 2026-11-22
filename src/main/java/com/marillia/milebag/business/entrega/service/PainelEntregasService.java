package com.marillia.milebag.business.entrega.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.entrega.model.EntregaCorrigivelResponse;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.dispensa.service.DispensaEntregaService;
import com.marillia.milebag.business.retirada.service.RetiradaBagagemService;
import com.marillia.milebag.business.entrega.mapper.PainelEntregasMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.ItemPainelDetalheResponse;
import com.marillia.milebag.business.entrega.model.ItemPainelResponse;
import com.marillia.milebag.business.entrega.model.PainelEntregasResponse;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.business.viagem.service.ViagemService;
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
    private final ViagemService viagemService;
    private final PainelEntregasMapper mapper;
    private final DispensaEntregaService dispensaEntregaService;
    private final RetiradaBagagemService retiradaBagagemService;
    private final ComprovanteEntregaService comprovanteEntregaService;

    public PainelEntregasService(
            EntregaRepository entregaRepository,
            BdoService bdoService,
            BagagemCaronaService bagagemCaronaService,
            ViagemService viagemService,
            PainelEntregasMapper mapper,
            DispensaEntregaService dispensaEntregaService,
            RetiradaBagagemService retiradaBagagemService,
            ComprovanteEntregaService comprovanteEntregaService) {
        this.dispensaEntregaService = dispensaEntregaService;
        this.retiradaBagagemService = retiradaBagagemService;
        this.comprovanteEntregaService = comprovanteEntregaService;
        this.entregaRepository = entregaRepository;
        this.bdoService = bdoService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.viagemService = viagemService;
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
        // V47: quem estava com a tela aberta ve o motivo da saida, nao um "nao pertence" generico.
        EntregaService.exigirNaoCancelada(entrega);
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new AcessoNegadoException("Esta entrega nao pertence ao motorista autenticado.");
        }

        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
        List<BagagemCarona> bagagensCarona = bagagemCaronaService.listarPorEntrega(entregaId);
        return mapper.toDetalhe(
                entrega, bdo, bagagensCarona, this::bdoDe, viagemService.marcoMaisRecente(entregaId).orElse(null),
                dispensaEntregaService.exigeMotivo(entregaId),
                retiradaBagagemService.temRetiradaPendente(entregaId, motoristaId),
                corrigivelAte(entrega));
    }

    /** V51: as entregas que o motorista finalizou e ainda pode corrigir (24 h), as mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<EntregaCorrigivelResponse> listarCorrigiveis(UUID motoristaId) {
        Instant desde = Instant.now().minus(ComprovanteEntregaService.PRAZO_CORRECAO);
        return entregaRepository.findByMotoristaResponsavelAndStatusAndAtualizadoEmAfterOrderByAtualizadoEmDesc(
                        motoristaId, EntregaStatus.ENCERRADA_COM_SUCESSO, desde)
                .stream()
                .flatMap(entrega -> {
                    Instant ate = corrigivelAte(entrega);
                    if (ate == null) {
                        return Stream.empty();
                    }
                    Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
                    return Stream.of(new EntregaCorrigivelResponse(entrega.getId(), bdo.getNumero(), bdo.getPassageiroNome(), ate));
                })
                .toList();
    }

    private Instant corrigivelAte(Entrega entrega) {
        if (entrega.getStatus() != EntregaStatus.ENCERRADA_COM_SUCESSO) {
            return null;
        }
        return comprovanteEntregaService.corrigivelAte(entrega.getId()).filter(ate -> ate.isAfter(Instant.now())).orElse(null);
    }

    private ItemPainelResponse montarItem(Entrega entrega) {
        Bdo bdo = buscarBdoOuFalhar(entrega.getBdoId());
        List<BagagemCarona> bagagensCarona = bagagemCaronaService.listarPorEntrega(entrega.getId());
        String marcoMaisRecente = viagemService.marcoMaisRecente(entrega.getId()).orElse(null);
        return mapper.toItemPainel(entrega, bdo, bagagensCarona, this::bdoDe, marcoMaisRecente,
                retiradaBagagemService.temRetiradaPendente(entrega.getId(), entrega.getMotoristaResponsavel()));
    }

    /** Bagagem carona referencia o proprio BDO (nao o da entrega principal): etiqueta, numero e malas do resumo. */
    private Bdo bdoDe(UUID bdoId) {
        return bdoService.buscarPorId(bdoId).orElse(null);
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
