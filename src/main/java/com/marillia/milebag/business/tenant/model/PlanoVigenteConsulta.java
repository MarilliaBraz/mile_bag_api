package com.marillia.milebag.business.tenant.model;

/** Resultado interno de {@code TenantPlanoService.consultarVigente} - o controller mapeia para {@link PlanoVigenteResponse}. */
public record PlanoVigenteConsulta(PlanoAssinatura planoVigente, UsoAtual usoAtual, boolean dentroDoLimite) {
}
