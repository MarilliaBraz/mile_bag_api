package com.marillia.milebag.business.rota.model;

import java.util.List;
import java.util.UUID;

/** Ver contracts/rota-api.md. */
public record CalcularRotaResponse(UUID entregaId, Coordenada origem, Coordenada destino, List<RotaAlternativa> alternativas) {
}
