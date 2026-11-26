package com.marillia.milebag.business.painelgestao.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Viagem do periodo com o que ela custou (nulo se a tarifa/pedagio ainda nao se resolve) e quantas malas levou. */
public record ViagemPeriodo(
        UUID trajetoId,
        UUID motoristaId,
        Instant inicio,
        Instant encerradaEm,
        double km,
        long malas,
        boolean entregue,
        BigDecimal pagamento,
        BigDecimal pedagio) {
}
