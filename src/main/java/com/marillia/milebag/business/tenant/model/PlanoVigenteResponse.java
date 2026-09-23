package com.marillia.milebag.business.tenant.model;

/** Response de GET /api/v1/plano (HU16) - consulta do plano vigente do tenant autenticado. */
public record PlanoVigenteResponse(PlanoAssinaturaResponse planoVigente, UsoAtual usoAtual, boolean dentroDoLimite) {
}
