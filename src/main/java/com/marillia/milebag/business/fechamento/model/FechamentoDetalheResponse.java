package com.marillia.milebag.business.fechamento.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ver contracts/fechamento-api.md, GET /fechamentos/{id}. */
public record FechamentoDetalheResponse(
        UUID id, LocalDate periodoInicio, LocalDate periodoFim, Instant geradoEm, StatusFechamento status, List<ItemFechamentoResponse> itens) {
}
