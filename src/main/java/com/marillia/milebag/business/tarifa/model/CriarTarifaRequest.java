package com.marillia.milebag.business.tarifa.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Ver contracts/tarifas-api.md. */
public record CriarTarifaRequest(
        EscopoTarifa escopo, UUID motoristaId, String regiao, BigDecimal valorFixo, BigDecimal valorPorKm, LocalDate vigenteDesde) {
}
