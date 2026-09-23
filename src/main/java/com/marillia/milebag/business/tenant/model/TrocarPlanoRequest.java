package com.marillia.milebag.business.tenant.model;

import java.util.UUID;

/** Request de PUT /api/v1/plataforma/tenants/{tenantId}/plano (HU16). */
public record TrocarPlanoRequest(UUID planoId) {
}
