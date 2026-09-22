package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdicionarValorRequest(BigDecimal valor, LocalDate vigenteDesde) {
}
