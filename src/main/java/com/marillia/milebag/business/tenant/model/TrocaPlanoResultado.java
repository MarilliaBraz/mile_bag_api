package com.marillia.milebag.business.tenant.model;

/** Resultado interno de {@code TenantPlanoService.trocarPlano} - o controller mapeia para {@link TrocarPlanoResponse}. */
public record TrocaPlanoResultado(PlanoAssinatura planoVigente, boolean usoExcedeuLimiteNaTroca) {
}
