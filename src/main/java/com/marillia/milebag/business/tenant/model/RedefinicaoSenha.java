package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Pedido de redefinicao de senha (HU25). Nao estende {@code BaseEntity}: nao tem RLS (ver V31) e o tenant e um
 * dado da propria linha. So o hash do token e guardado. Uso unico: {@link #marcarUsada} invalida o link.
 */
@Entity
@Table(name = "redefinicao_senha")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedefinicaoSenha {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    public RedefinicaoSenha(UUID tenantId, UUID usuarioId, String tokenHash, Instant criadoEm, Instant expiraEm) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public boolean estaValida(Instant agora) {
        return usadoEm == null && agora.isBefore(expiraEm);
    }

    public void marcarUsada(Instant agora) {
        this.usadoEm = agora;
    }
}
