package com.marillia.milebag.business.encaminhamento.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustoProcessoResponse(UUID id, TipoCusto tipo, BigDecimal valor, String descricao, LocalDate data, Instant lancadoEm) {

    public static CustoProcessoResponse de(CustoProcesso custo) {
        return new CustoProcessoResponse(custo.getId(), custo.getTipo(), custo.getValor(), custo.getDescricao(), custo.getData(),
                custo.getCriadoEm());
    }
}
