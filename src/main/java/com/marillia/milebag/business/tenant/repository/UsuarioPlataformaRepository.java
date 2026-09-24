package com.marillia.milebag.business.tenant.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.tenant.model.UsuarioPlataforma;

/** Como {@link TenantRepository}, nao estende {@code BaseRepository}: a entidade nao e tenant-scoped. */
public interface UsuarioPlataformaRepository extends JpaRepository<UsuarioPlataforma, UUID> {

    Optional<UsuarioPlataforma> findByLogin(String login);
}
