package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Formato de resposta do POST e do GET (contracts/tenants-api.md).
 * {@code planoId} e passado explicitamente (nao lido de {@code
 * Tenant.getPlanoId()}, que nao existe mais desde HU16/T016) - o chamador
 * o busca via {@code TenantPlanoService.planoIdVigente}.
 */
public record TenantResponse(UUID id, String identificadorEmpresa, String nome, UUID planoId, String status, Instant criadoEm) {

    public static TenantResponse de(Tenant tenant, UUID planoId) {
        return new TenantResponse(
                tenant.getId(), tenant.getIdentificadorEmpresa(), tenant.getNome(), planoId, tenant.getStatus().name(), tenant.getCriadoEm());
    }
}
