package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Servidor de tiles do mapa impresso na ficha da bagagem encontrada (HU36). {@code url} tem {@code {z}}, {@code {x}} e
 * {@code {y}}; por padrao o OpenStreetMap, o mesmo do mapa do app, cuja politica de uso exige identificar a aplicacao
 * ({@code userAgent}) e creditar o OpenStreetMap (https://operations.osmfoundation.org/policies/tiles/).
 */
@ConfigurationProperties(prefix = "milebag.rotas.tiles")
public record TilesProperties(String url, String userAgent) {
}
