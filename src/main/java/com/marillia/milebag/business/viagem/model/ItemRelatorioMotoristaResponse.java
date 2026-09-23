package com.marillia.milebag.business.viagem.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Uma entrega do relatorio quinzenal do motorista (HU17), com os valores atualmente vigentes. */
public record ItemRelatorioMotoristaResponse(
        UUID entregaId,
        String bdoNumero,
        BigDecimal kmIda,
        BigDecimal kmVolta,
        BigDecimal tarifaAplicada,
        BigDecimal valorPedagio,
        boolean temAjuste) {
}
