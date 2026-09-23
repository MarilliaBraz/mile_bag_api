package com.marillia.milebag.business.tenant.model;

import java.util.UUID;

/**
 * {@code emailAdministrador}: nao fazia parte do contrato original desta
 * HU (HU14) - adicionado para atender HU15 (FR-001), que dispara o convite
 * do primeiro administrador do tenant como parte desta MESMA operacao
 * (contracts/convite-api.md: "efeito colateral de POST .../tenants", sem
 * chamada separada). Sem este campo, HU15 nao teria destinatario algum
 * para o convite.
 */
public record CadastroTenantRequest(String identificadorEmpresa, String nome, UUID planoId, String emailAdministrador) {
}
