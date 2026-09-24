package com.marillia.milebag.business.tenant.model;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mapa login -> empresa (HU26), so leitura: quem escreve nela e o trigger de {@code usuario} (V32). Nao estende
 * {@code BaseEntity} (sem RLS: e justamente o que permite achar o tenant antes de haver um no contexto).
 */
@Entity
@Immutable
@Table(name = "usuario_login")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsuarioLogin {

    /** O login em minusculas. */
    @Id
    @Column(length = 320)
    private String login;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;
}
