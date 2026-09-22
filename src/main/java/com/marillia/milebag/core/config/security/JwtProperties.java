package com.marillia.milebag.core.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Segredo e expiracao do JWT sempre vindos de configuracao externa (variavel
 * de ambiente), nunca hardcoded - convencao dos "3 pilares" (parametrizacao),
 * ver {@code CONVENTIONS.md} secao 2.3.
 */
@ConfigurationProperties(prefix = "milebag.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
