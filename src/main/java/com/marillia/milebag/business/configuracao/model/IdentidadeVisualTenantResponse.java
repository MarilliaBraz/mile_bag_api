package com.marillia.milebag.business.configuracao.model;

import java.time.Instant;

/** Formato de resposta do GET e do PUT (contracts/identidade-visual-api.md). */
public record IdentidadeVisualTenantResponse(String logotipoUrl, String cabecalho, Instant atualizadoEm) {

    private static final String LOGOTIPO_ENDPOINT = "/api/v1/identidade-visual/logotipo";

    /** {@code null}: tenant sem identidade configurada (FR-004, Response 200 "sem configuracao"). */
    public static IdentidadeVisualTenantResponse vazia() {
        return new IdentidadeVisualTenantResponse(null, null, null);
    }

    public static IdentidadeVisualTenantResponse de(IdentidadeVisualTenant identidade) {
        String logotipoUrl = identidade.getLogotipoPath() != null ? LOGOTIPO_ENDPOINT : null;
        return new IdentidadeVisualTenantResponse(logotipoUrl, identidade.getCabecalho(), identidade.getAtualizadoEm());
    }
}
