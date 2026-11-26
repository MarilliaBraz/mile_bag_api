package com.marillia.milebag.business.tenant.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.core.base.BaseRepository;

public interface UsuarioRepository extends BaseRepository<Usuario, UUID> {

    /** Filtrado por tenant pela RLS: so enxerga o tenant do {@code TenantContext} da transacao. */
    List<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findByPapelOrderByNomeAscEmailAsc(String papel);

    /** "Motoristas ativos" do tenant (HU16): contas de motorista nao desativadas. */
    long countByPapelAndAtivoTrue(String papel);

    /** V62: ultima vez que a conta usou o app (painel de gestao, motoristas online). */
    @Modifying
    @Query(value = "UPDATE usuario SET ultimo_acesso_em = :quando WHERE id = :id", nativeQuery = true)
    int registrarAcesso(@Param("id") UUID id, @Param("quando") Instant quando);
}
