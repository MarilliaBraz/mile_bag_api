package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ver contracts/pedagios-api.md. {@code valorVigente}/{@code vigenteDesde}
 * ficam nulos quando a praca so tem valores com vigencia futura (nenhum
 * ainda vigente hoje).
 */
public record PracaPedagioResponse(UUID id, String nome, BigDecimal valorVigente, LocalDate vigenteDesde) {

    public static PracaPedagioResponse de(PracaPedagio praca, PracaPedagioValor valorVigente) {
        return new PracaPedagioResponse(
                praca.getId(),
                praca.getNome(),
                valorVigente == null ? null : valorVigente.getValor(),
                valorVigente == null ? null : valorVigente.getVigenteDesde());
    }
}
