package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CriarPracaPedagioRequest(String nome, BigDecimal valor, LocalDate vigenteDesde) {
}
