package com.marillia.milebag.business.tenant.model;

/**
 * Item de GET /api/v1/plataforma/planos (HU16) - inclui a contagem de
 * tenants vigentes, usada na UI antes de tentar remover (contracts/planos-api.md).
 * So esta listagem carrega a contagem; os demais usos de {@link
 * PlanoAssinaturaResponse} (POST, troca de plano, consulta do vigente) nao.
 */
public record PlanoAssinaturaListItemResponse(PlanoAssinaturaResponse plano, long tenantsAssociados) {

    public static PlanoAssinaturaListItemResponse de(PlanoAssinatura plano, long tenantsAssociados) {
        return new PlanoAssinaturaListItemResponse(PlanoAssinaturaResponse.de(plano), tenantsAssociados);
    }
}
