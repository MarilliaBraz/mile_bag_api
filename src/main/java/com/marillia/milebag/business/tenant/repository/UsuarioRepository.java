package com.marillia.milebag.business.tenant.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.core.base.BaseRepository;

public interface UsuarioRepository extends BaseRepository<Usuario, UUID> {

    /** Filtrado por tenant pela RLS: so enxerga o tenant do {@code TenantContext} da transacao. */
    List<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findByPapelOrderByNomeAscEmailAsc(String papel);
}
