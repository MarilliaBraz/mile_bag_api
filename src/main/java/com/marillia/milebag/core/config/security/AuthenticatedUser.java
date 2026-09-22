package com.marillia.milebag.core.config.security;

import java.util.UUID;

/**
 * Identidade do usuario autenticado, extraida do JWT. Usado como principal
 * da autenticacao do Spring Security - ver {@link JwtAuthenticationFilter}.
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, String papel) {
}
