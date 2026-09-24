package com.marillia.milebag.business.tenant.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.tenant.model.RedefinicaoSenha;

/** Nao estende {@code BaseRepository}: a entidade nao e tenant-scoped (ver V31). */
public interface RedefinicaoSenhaRepository extends JpaRepository<RedefinicaoSenha, UUID> {

    Optional<RedefinicaoSenha> findByTokenHash(String tokenHash);

    List<RedefinicaoSenha> findByUsuarioIdAndUsadoEmIsNull(UUID usuarioId);
}
