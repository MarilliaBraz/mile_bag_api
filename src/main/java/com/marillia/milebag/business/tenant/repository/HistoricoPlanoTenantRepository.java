package com.marillia.milebag.business.tenant.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.tenant.model.HistoricoPlanoTenant;

public interface HistoricoPlanoTenantRepository extends JpaRepository<HistoricoPlanoTenant, UUID> {

    /** O registro vigente de um tenant - no maximo um por tenant (indice unico parcial, migracao V24). */
    Optional<HistoricoPlanoTenant> findByTenantIdAndVigenteAteIsNull(UUID tenantId);

    /** Usado por {@code PlanoAssinaturaService.remover} (FR-005) e por {@code listar} (contagem exibida na UI). */
    long countByPlanoIdAndVigenteAteIsNull(UUID planoId);
}
