package com.marillia.milebag.business.viagem.model;

import java.time.LocalDate;
import java.util.List;

/** Relatorio sob demanda (nao persistido) das entregas do motorista com trajeto consolidado no periodo (HU17, FR-001). */
public record RelatorioQuinzenalResponse(LocalDate periodoInicio, LocalDate periodoFim, List<ItemRelatorioMotoristaResponse> entregas) {
}
