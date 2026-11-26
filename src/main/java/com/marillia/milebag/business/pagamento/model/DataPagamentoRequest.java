package com.marillia.milebag.business.pagamento.model;

import java.time.LocalDate;

/** Data do agendamento ou do pagamento (no pago, vazia = hoje). */
public record DataPagamentoRequest(LocalDate data) {
}
