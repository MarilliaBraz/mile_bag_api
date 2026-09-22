package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Convite de ativacao de conta do primeiro administrador de um tenant
 * (HU15) - ver data-model.md. {@code token} E o identificador primario
 * (nao ha um {@code id} separado): e opaco, gerado por {@link
 * com.marillia.milebag.business.tenant.service.TokenConviteGenerator}, e a
 * unica credencial exigida pelos dois endpoints publicos desta HU. Ver V23
 * (migracao) para a decisao de esta tabela nao ter Row Level Security.
 */
@Entity
@Table(name = "convite_administrador")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConviteAdministrador {

    @Id
    private UUID token;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "email_destinatario", updatable = false, length = 320)
    private String emailDestinatario;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConviteStatus status;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", updatable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    public ConviteAdministrador(UUID token, UUID tenantId, String emailDestinatario, Instant expiraEm) {
        this.token = token;
        this.tenantId = tenantId;
        this.emailDestinatario = emailDestinatario;
        this.status = ConviteStatus.PENDENTE;
        this.expiraEm = expiraEm;
    }

    public boolean expirado() {
        return Instant.now().isAfter(expiraEm);
    }

    public void marcarExpirado() {
        this.status = ConviteStatus.EXPIRADO;
    }

    public void marcarUsado() {
        this.status = ConviteStatus.USADO;
        this.usadoEm = Instant.now();
    }

    public void invalidar() {
        this.status = ConviteStatus.INVALIDADO;
    }
}
