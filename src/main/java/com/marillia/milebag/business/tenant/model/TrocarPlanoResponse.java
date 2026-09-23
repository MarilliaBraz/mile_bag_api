package com.marillia.milebag.business.tenant.model;

/** Response de PUT /api/v1/plataforma/tenants/{tenantId}/plano (HU16, FR-006). */
public record TrocarPlanoResponse(PlanoAssinaturaResponse planoVigente, boolean usoExcedeuLimiteNaTroca) {
}
