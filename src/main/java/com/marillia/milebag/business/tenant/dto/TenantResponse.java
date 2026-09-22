package com.marillia.milebag.business.tenant.dto;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.business.tenant.model.Tenant;

/** Formato de resposta do POST e do GET (contracts/tenants-api.md). */
public record TenantResponse(UUID id, String identificadorEmpresa, String nome, UUID planoId, String status, Instant criadoEm) {

    public static TenantResponse de(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getIdentificadorEmpresa(),
                tenant.getNome(),
                tenant.getPlanoId(),
                tenant.getStatus().name(),
                tenant.getCriadoEm());
    }
}
