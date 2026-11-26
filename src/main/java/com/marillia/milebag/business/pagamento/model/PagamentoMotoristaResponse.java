package com.marillia.milebag.business.pagamento.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pagamento de um motorista num fechamento: totais das entregas calculadas (pagamento e reembolso de pedagio sempre
 * separados, FR-003), a situacao e o rastro.
 */
public record PagamentoMotoristaResponse(
        UUID motoristaId,
        BigDecimal totalPagamentoEntregas,
        BigDecimal totalReembolsoPedagios,
        SituacaoPagamento situacao,
        LocalDate dataAgendada,
        LocalDate pagoEm,
        List<EventoPagamentoResponse> eventos) {
}
