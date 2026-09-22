package com.marillia.milebag.core.config.security;

import java.util.UUID;

/**
 * Identidade do usuario autenticado, extraida do JWT. Usado como principal
 * da autenticacao do Spring Security - ver {@link JwtAuthenticationFilter}.
 *
 * <p>{@code tenantId} e nulo para o perfil administrador da plataforma
 * (HU14) - esse usuario nao e escopado a nenhum tenant especifico.</p>
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, String papel) {
}
