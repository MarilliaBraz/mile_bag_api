package com.marillia.milebag.business.viagem.model;

import java.time.Instant;
import java.util.UUID;

public record CheckpointResponse(UUID id, TipoCheckpoint tipo, Double lat, Double lng, Instant dataHora, boolean ausente) {
}
