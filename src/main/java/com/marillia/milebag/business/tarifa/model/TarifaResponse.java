package com.marillia.milebag.business.tarifa.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Ver contracts/tarifas-api.md. */
public record TarifaResponse(
        UUID id, EscopoTarifa escopo, UUID motoristaId, String regiao, BigDecimal valorFixo, BigDecimal valorPorKm, LocalDate vigenteDesde) {

    public static TarifaResponse de(TabelaTarifa tarifa) {
        return new TarifaResponse(
                tarifa.getId(),
                tarifa.getEscopo(),
                tarifa.getMotoristaId(),
                tarifa.getRegiao(),
                tarifa.getValorFixo(),
                tarifa.getValorPorKm(),
                tarifa.getVigenteDesde());
    }
}
