package com.marillia.milebag.business.tenant.model;

/** Request de POST /api/v1/plataforma/planos (HU16). */
public record CriarPlanoRequest(String nome, int limiteMotoristasAtivos, int limiteVolumeMensalBdos) {
}
