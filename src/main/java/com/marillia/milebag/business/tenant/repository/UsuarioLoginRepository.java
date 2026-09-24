package com.marillia.milebag.business.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.tenant.model.UsuarioLogin;

/** Nao estende {@code BaseRepository}: o mapa nao e tenant-scoped (ver V32). Chave: o login em minusculas. */
public interface UsuarioLoginRepository extends JpaRepository<UsuarioLogin, String> {
}
