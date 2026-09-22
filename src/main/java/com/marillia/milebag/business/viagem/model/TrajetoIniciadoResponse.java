package com.marillia.milebag.business.viagem.model;

import java.time.Instant;
import java.util.UUID;

/** Ver contracts/viagem-api.md, resposta de POST .../viagem/iniciar. */
public record TrajetoIniciadoResponse(
        UUID id,
        TrajetoStatus status,
        Instant dataInicio,
        CalculoRotaResponse calculoRotaInicial,
        CheckpointResponse checkpointPartida) {
}
