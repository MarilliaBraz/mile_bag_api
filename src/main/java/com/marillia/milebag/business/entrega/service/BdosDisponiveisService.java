package com.marillia.milebag.business.entrega.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.business.entrega.mapper.PainelEntregasMapper;
import com.marillia.milebag.business.entrega.model.BdosDisponiveisResponse;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.ItemBdoDisponivelResponse;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.util.EnderecoComparavel;

/**
 * Lista de BDOs confirmados sem motorista responsavel (HU19, FR-007) - a
 * unica via de descoberta, na interface, de um BDO recebido automaticamente
 * por e-mail (RF21/HU02). Injeta {@code BdoRepository} direto (nao
 * BdoService): esta consulta e sobre BDO do proprio dominio de business/bdo,
 * nao um acesso cross-domain (CONVENTIONS.md 2.1) - diferente de
 * PainelEntregasService, que consulta Bdo a partir de business/entrega.
 */
@Service
public class BdosDisponiveisService {

    private static final Logger log = LoggerFactory.getLogger(BdosDisponiveisService.class);

    private final BdoRepository bdoRepository;
    private final EntregaRepository entregaRepository;
    private final PainelEntregasMapper mapper;

    private final UsuarioConsultaService usuarioConsultaService;

    public BdosDisponiveisService(BdoRepository bdoRepository, EntregaRepository entregaRepository, PainelEntregasMapper mapper,
            UsuarioConsultaService usuarioConsultaService) {
        this.usuarioConsultaService = usuarioConsultaService;
        this.bdoRepository = bdoRepository;
        this.entregaRepository = entregaRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public BdosDisponiveisResponse consultarDisponiveis(UUID motoristaId) {
        // V52: so os BDOs da base do motorista; sem base, nenhum (a empresa precisa definir a base dele).
        Optional<UUID> base = usuarioConsultaService.baseDoMotorista(motoristaId);
        if (base.isEmpty()) {
            return new BdosDisponiveisResponse(List.of(), true);
        }
        var bdos = disponiveisDaBase(base.get()).stream().map(this::toItem).toList();
        log.info("Consulta de BDOs disponiveis: {} encontrado(s)", bdos.size());
        return new BdosDisponiveisResponse(bdos, false);
    }

    /**
     * Item 8: BDOs disponiveis da base do motorista que vao para o mesmo endereco da entrega dele - sugeridos como
     * bagagem carona (o motorista confirma; nada e vinculado sozinho).
     */
    @Transactional(readOnly = true)
    public List<ItemBdoDisponivelResponse> sugestoesDeCarona(UUID entregaId, UUID motoristaId) {
        Entrega entrega = entregaRepository.findById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new AcessoNegadoException("Esta entrega nao pertence ao motorista autenticado.");
        }
        if (entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO || entrega.getStatus() == EntregaStatus.CANCELADA) {
            return List.of();
        }
        Optional<UUID> base = usuarioConsultaService.baseDoMotorista(motoristaId);
        Optional<EnderecoComparavel> destino = bdoRepository.findById(entrega.getBdoId())
                .flatMap(bdo -> EnderecoComparavel.de(bdo.getEnderecoEntrega(), bdo.getCidade(), bdo.getCep()));
        if (base.isEmpty() || destino.isEmpty()) {
            return List.of();
        }
        List<ItemBdoDisponivelResponse> sugestoes = disponiveisDaBase(base.get()).stream()
                .filter(bdo -> !bdo.getId().equals(entrega.getBdoId()))
                .filter(bdo -> EnderecoComparavel.de(bdo.getEnderecoEntrega(), bdo.getCidade(), bdo.getCep())
                        .map(destino.get()::mesmoLocal)
                        .orElse(false))
                .map(this::toItem)
                .toList();
        log.info("Sugestoes de carona para a entrega {}: {}", entregaId, sugestoes.size());
        return sugestoes;
    }

    private List<Bdo> disponiveisDaBase(UUID base) {
        return bdoRepository.findDisponiveisParaAutoatribuicao().stream()
                .filter(bdo -> bdo.getBaseId() == null || bdo.getBaseId().equals(base))
                .toList();
    }

    // A entrega reaberta (RN05) traz a janela de reagendamento (HU29); a consulta so devolve BDO sem entrega ou com entrega reaberta.
    // So a entrega REABERTA e uma nova tentativa: a devolvida por dispensa antes de qualquer viagem
    // (AGUARDANDO_ATRIBUICAO, V46) aparece como uma entrega nova.
    private ItemBdoDisponivelResponse toItem(Bdo bdo) {
        return mapper.toItemDisponivel(bdo, entregaRepository.findByBdoId(bdo.getId())
                .filter(entrega -> entrega.getStatus() == EntregaStatus.REABERTA)
                .orElse(null));
    }
}
