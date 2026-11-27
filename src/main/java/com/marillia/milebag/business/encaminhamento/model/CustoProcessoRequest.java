package com.marillia.milebag.business.encaminhamento.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** V65: lancamento de um custo; data vazia = hoje. */
public record CustoProcessoRequest(String tipo, BigDecimal valor, String descricao, LocalDate data) {
}
