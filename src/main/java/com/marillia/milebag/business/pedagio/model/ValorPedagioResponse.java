package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ver contracts/pedagios-api.md. */
public record ValorPedagioResponse(BigDecimal valor, LocalDate vigenteDesde) {

    public static ValorPedagioResponse de(PracaPedagioValor valor) {
        return new ValorPedagioResponse(valor.getValor(), valor.getVigenteDesde());
    }
}
