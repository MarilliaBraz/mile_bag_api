package com.marillia.milebag.business.entrega.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaEncontradaResponse;
import com.marillia.milebag.business.pagamento.service.PagamentoMotoristaService;

/**
 * Item 4c: o motorista acha pelo numero do BDO uma entrega sua de qualquer epoca (em andamento, concluida ou ja paga).
 * So as que ele e o responsavel: BDO de outro motorista, nao assumido ou cancelado (fica sem motorista) nao aparece.
 */
@Service
public class BuscaBdoMotoristaService {

    private final BdoService bdoService;
    private final EntregaService entregaService;
    private final PagamentoMotoristaService pagamentoMotoristaService;

    public BuscaBdoMotoristaService(BdoService bdoService, EntregaService entregaService, PagamentoMotoristaService pagamentoMotoristaService) {
        this.bdoService = bdoService;
        this.entregaService = entregaService;
        this.pagamentoMotoristaService = pagamentoMotoristaService;
    }

    @Transactional(readOnly = true)
    public List<EntregaEncontradaResponse> buscar(UUID motoristaId, String numero) {
        List<UUID> bdoIds = bdoService.buscarPorNumero(numero).stream().map(BdoResponse::id).toList();
        List<Entrega> minhas = bdoIds.stream()
                .map(entregaService::buscarPorBdo)
                .flatMap(Optional::stream)
                .filter(entrega -> motoristaId.equals(entrega.getMotoristaResponsavel()))
                .toList();
        if (minhas.isEmpty()) {
            return List.of();
        }
        Map<UUID, Bdo> bdos = bdoService.buscarPorIds(minhas.stream().map(Entrega::getBdoId).toList());
        Map<UUID, LocalDate> pagas = pagamentoMotoristaService.pagasEntre(minhas.stream().map(Entrega::getId).toList());
        return minhas.stream()
                .map(entrega -> {
                    Bdo bdo = bdos.get(entrega.getBdoId());
                    return new EntregaEncontradaResponse(entrega.getId(), bdo == null ? null : bdo.getNumero(), entrega.getStatus(),
                            bdo == null ? null : bdo.getPassageiroNome(), entrega.getAtualizadoEm(), pagas.get(entrega.getId()));
                })
                .sorted(Comparator.comparing(EntregaEncontradaResponse::atualizadoEm, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
