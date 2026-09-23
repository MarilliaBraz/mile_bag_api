package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

/** Formato de um plano de assinatura na API (HU16, contracts/planos-api.md). */
public record PlanoAssinaturaResponse(
        UUID id, String nome, int limiteMotoristasAtivos, int limiteVolumeMensalBdos, Instant criadoEm) {

    public static PlanoAssinaturaResponse de(PlanoAssinatura plano) {
        return new PlanoAssinaturaResponse(
                plano.getId(), plano.getNome(), plano.getLimiteMotoristasAtivos(), plano.getLimiteVolumeMensalBdos(), plano.getCriadoEm());
    }
}
