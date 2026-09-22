package com.marillia.milebag.business.fechamento.model;

import java.time.LocalDate;

public record GerarFechamentoRequest(LocalDate periodoInicio, LocalDate periodoFim) {
}
