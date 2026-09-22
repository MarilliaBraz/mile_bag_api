package com.marillia.milebag.business.viagem.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.rota.model.TipoSelecao;

public record CalculoRotaResponse(
        UUID id,
        BigDecimal kmIda,
        BigDecimal kmVolta,
        List<TrechoPedagio> pracasPedagio,
        long tempoEstimado,
        TipoSelecao origemSelecao,
        Instant dataHora,
        boolean ehRotaInicial) {
}
