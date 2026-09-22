package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Servico de geocodificacao open source escolhido como default (Nominatim) -
 * ver research.md da feature 001, secao 5. {@code userAgent} identifica a
 * aplicacao nas requisicoes, exigido pela politica de uso do Nominatim
 * publico (https://operations.osmfoundation.org/policies/nominatim/).
 */
@ConfigurationProperties(prefix = "milebag.rotas.nominatim")
public record NominatimProperties(String baseUrl, String userAgent) {
}
