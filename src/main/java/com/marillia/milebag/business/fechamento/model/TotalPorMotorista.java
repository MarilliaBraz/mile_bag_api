package com.marillia.milebag.business.fechamento.model;

import java.math.BigDecimal;
import java.util.UUID;

/** FR-003: pagamento de entregas e reembolso de pedagio sempre separados, nunca somados. */
public record TotalPorMotorista(UUID motoristaId, BigDecimal totalPagamentoEntregas, BigDecimal totalReembolsoPedagios) {
}
