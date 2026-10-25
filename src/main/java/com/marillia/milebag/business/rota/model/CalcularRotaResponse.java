package com.marillia.milebag.business.rota.model;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.TracadoAlternativa;

/**
 * Ver contracts/rota-api.md. {@code motorRotasDisponivel = false} indica que a lista veio vazia porque o
 * motor de rotas esta fora do ar, nao por falta de caminho. {@code tracados} traz o caminho de cada
 * alternativa (simplificado) so para desenhar no mapa - nao e gravado.
 */
public record CalcularRotaResponse(UUID entregaId, Coordenada origem, Coordenada destino, List<RotaAlternativa> alternativas,
        boolean motorRotasDisponivel, List<TracadoAlternativa> tracados) {
}
