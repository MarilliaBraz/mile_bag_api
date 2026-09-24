package com.marillia.milebag.core.config.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Identidade do usuario autenticado, extraida do JWT. Usado como principal
 * da autenticacao do Spring Security - ver {@link JwtAuthenticationFilter}.
 *
 * <p>{@code tenantId} e nulo para o perfil administrador da plataforma
 * (HU14) - esse usuario nao e escopado a nenhum tenant especifico. {@code emitidoEm}
 * e o {@code iat} do token (HU23: sessoes anteriores a uma revogacao deixam de valer).</p>
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, String papel, Instant emitidoEm) {

    public AuthenticatedUser(UUID userId, UUID tenantId, String papel) {
        this(userId, tenantId, papel, null);
    }
}
