package com.marillia.milebag.business.tarifa.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ver contracts/tarifas-api.md - PUT so atualiza valores/vigencia, nunca escopo/motoristaId/regiao. */
public record AtualizarTarifaRequest(BigDecimal valorFixo, BigDecimal valorPorKm, LocalDate vigenteDesde) {
}
