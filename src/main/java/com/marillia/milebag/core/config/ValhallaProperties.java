package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Servico de rotas open source escolhido como default (Valhalla) - ver
 * research.md da feature 001, secao 5.
 */
@ConfigurationProperties(prefix = "milebag.rotas.valhalla")
public record ValhallaProperties(String baseUrl) {
}
