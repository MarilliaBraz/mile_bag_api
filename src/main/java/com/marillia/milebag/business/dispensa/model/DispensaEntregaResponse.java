package com.marillia.milebag.business.dispensa.model;

import java.time.Instant;
import java.util.UUID;

/** Uma dispensa no historico da entrega, ja com os nomes para exibir (motorista e motivo). */
public record DispensaEntregaResponse(
        UUID id,
        UUID entregaId,
        String motoristaNome,
        String motivo,
        String complemento,
        boolean comBagagem,
        Instant dataHora) {
}
