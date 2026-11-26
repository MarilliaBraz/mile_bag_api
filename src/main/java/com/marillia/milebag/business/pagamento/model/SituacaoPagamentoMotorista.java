package com.marillia.milebag.business.pagamento.model;

import java.time.LocalDate;

/** O que o motorista ve no relatorio da quinzena: agendado para, pago em (nulo enquanto a agendar). */
public record SituacaoPagamentoMotorista(SituacaoPagamento situacao, LocalDate dataAgendada, LocalDate pagoEm) {
}
