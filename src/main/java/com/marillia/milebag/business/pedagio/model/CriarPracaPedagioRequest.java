package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ver contracts/pedagios-api.md. {@code latitude}/{@code longitude} (HU24) sao opcionais, mas vem juntas. */
public record CriarPracaPedagioRequest(String nome, BigDecimal valor, LocalDate vigenteDesde, Double latitude, Double longitude) {

    public CriarPracaPedagioRequest(String nome, BigDecimal valor, LocalDate vigenteDesde) {
        this(nome, valor, vigenteDesde, null, null);
    }
}
