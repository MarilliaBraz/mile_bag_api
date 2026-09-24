package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Conta do administrador da plataforma (HU22). Como {@link Tenant}, NAO estende
 * {@code BaseEntity}: nao pertence a nenhum tenant, entao nao ha {@code tenantId}
 * nem Row Level Security (ver V27).
 */
@Entity
@Table(name = "usuario_plataforma")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsuarioPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(updatable = false, length = 100)
    private String login;

    @Column(name = "senha_hash", updatable = false, length = 100)
    private String senhaHash;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;

    public UsuarioPlataforma(String login, String senhaHash) {
        this.login = login;
        this.senhaHash = senhaHash;
    }
}
