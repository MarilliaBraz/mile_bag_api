package com.marillia.milebag.business.notificacao.model;

import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Inscricao Web Push de um aparelho de motorista (HU32, RD18) - ver V39. */
@Entity
@Table(name = "inscricao_push")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InscricaoPush extends BaseEntity {

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, updatable = false)
    private String endpoint;

    @Column(name = "chave_p256dh", nullable = false)
    private String chaveP256dh;

    @Column(name = "chave_auth", nullable = false)
    private String chaveAuth;

    public InscricaoPush(UUID usuarioId, String endpoint, String chaveP256dh, String chaveAuth) {
        this.usuarioId = usuarioId;
        this.endpoint = endpoint;
        this.chaveP256dh = chaveP256dh;
        this.chaveAuth = chaveAuth;
    }

    /** RD18: o aparelho que se inscreve de novo (outra conta, ou chaves renovadas pelo navegador) e o mesmo registro. */
    public void reatribuir(UUID usuarioId, String chaveP256dh, String chaveAuth) {
        this.usuarioId = usuarioId;
        this.chaveP256dh = chaveP256dh;
        this.chaveAuth = chaveAuth;
    }
}
