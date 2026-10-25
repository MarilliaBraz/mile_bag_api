package com.marillia.milebag.core.geo;

import java.util.List;

/**
 * Traçado de uma alternativa de rota, para desenhar no mapa (HU03). Fica fora de {@link RotaAlternativa} de
 * proposito: a alternativa e gravada na proposta e no historico da viagem, e o traçado so serve para exibir.
 */
public record TracadoAlternativa(String alternativaId, List<Coordenada> pontos) {
}
