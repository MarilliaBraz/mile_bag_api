package com.marillia.milebag.core.config.security;

import java.util.UUID;

/**
 * Guarda o tenant da requisicao em andamento (resolvido uma unica vez, a
 * partir do JWT, por {@link JwtAuthenticationFilter}).
 *
 * <p>Nunca aceitar um tenantId vindo de parametro de request/body: o unico
 * ponto de entrada legitimo deste valor e o filtro de autenticacao - ver
 * {@code research.md} da feature 001, secao 3 ("Boas praticas a aplicar na
 * implementacao").</p>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static UUID get() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
