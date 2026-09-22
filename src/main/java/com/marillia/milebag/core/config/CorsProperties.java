package com.marillia.milebag.core.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origens de front-end autorizadas a chamar a API entre dominios (CORS) -
 * nunca hardcoded, ver CONVENTIONS.md secao 2.3 (parametrizacao). Sem essa
 * configuracao, qualquer front-end servido de uma origem diferente da API
 * (caso normal em desenvolvimento - Vite roda em outra porta - e em
 * producao, onde front e API costumam estar em dominios distintos) tem o
 * preflight OPTIONS recusado pelo Spring Security antes mesmo de chegar a
 * um controller.
 */
@ConfigurationProperties(prefix = "milebag.cors")
public record CorsProperties(List<String> allowedOriginPatterns) {
}
