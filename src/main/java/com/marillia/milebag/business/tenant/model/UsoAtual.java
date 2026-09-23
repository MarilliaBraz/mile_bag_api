package com.marillia.milebag.business.tenant.model;

/** Uso operacional corrente de um tenant, contra os limites do plano vigente (HU16). */
public record UsoAtual(long motoristasAtivos, long bdosNoMesCorrente) {
}
