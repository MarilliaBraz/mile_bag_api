package com.marillia.milebag.business.tenant.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.tenant.model.Tenant;

/**
 * Diferente de todo outro repositorio do sistema, NAO estende {@code
 * BaseRepository} (que exige {@code T extends BaseEntity}) - {@link Tenant}
 * nao e uma entidade tenant-scoped, e a propria definicao de tenant.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByIdentificadorEmpresa(String identificadorEmpresa);
}
