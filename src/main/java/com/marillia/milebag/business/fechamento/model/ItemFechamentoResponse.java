package com.marillia.milebag.business.fechamento.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Ver contracts/fechamento-api.md. */
public record ItemFechamentoResponse(
        UUID id,
        UUID entregaId,
        UUID motoristaId,
        BigDecimal valorPagamentoEntrega,
        BigDecimal valorReembolsoPedagio,
        StatusItemFechamento statusItem,
        String motivoPendencia,
        boolean tentativaSemSucesso) {

    public static ItemFechamentoResponse de(ItemFechamento item) {
        return new ItemFechamentoResponse(
                item.getId(),
                item.getEntregaId(),
                item.getMotoristaId(),
                item.getValorPagamentoEntrega(),
                item.getValorReembolsoPedagio(),
                item.getStatusItem(),
                item.getMotivoPendencia(),
                item.isTentativaSemSucesso());
    }
}
