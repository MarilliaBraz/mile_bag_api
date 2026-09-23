package com.marillia.milebag.business.rota.model;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.RotaAlternativa;

/** Ver contracts/rota-api.md. */
public record CalcularRotaResponse(UUID entregaId, Coordenada origem, Coordenada destino, List<RotaAlternativa> alternativas) {
}
