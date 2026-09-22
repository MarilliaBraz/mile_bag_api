package com.marillia.milebag.business.tenant.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.tenant.model.ConviteAdministrador;
import com.marillia.milebag.business.tenant.model.ConviteStatus;

/** Nao estende {@code BaseRepository}: {@link ConviteAdministrador} nao e {@code BaseEntity} (sem RLS - ver V23). */
public interface ConviteAdministradorRepository extends JpaRepository<ConviteAdministrador, UUID> {

    Optional<ConviteAdministrador> findByTenantIdAndStatus(UUID tenantId, ConviteStatus status);

    /** Usado por {@code reenviar} (nao ha convite PENDENTE ativo, mas ha historico) para reaproveitar o e-mail do ultimo convite. */
    Optional<ConviteAdministrador> findFirstByTenantIdOrderByCriadoEmDesc(UUID tenantId);
}
