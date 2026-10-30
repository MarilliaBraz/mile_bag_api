package com.marillia.milebag.business.viagem.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Link "Acompanhe sua bagagem" de uma tentativa de entrega (HU31, RD17). {@code codigo} e o identificador
 * primario e a unica credencial da pagina publica: aleatorio ({@link UUID#randomUUID()}), nunca sequencial.
 * Como {@code ConviteAdministrador}, nao estende {@code BaseEntity} - ver V38 para a decisao de esta tabela
 * nao ter Row Level Security.
 */
@Entity
@Table(name = "link_acompanhamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkAcompanhamento {

    @Id
    private UUID codigo;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "trajeto_id", updatable = false)
    private UUID trajetoId;

    @Column(name = "entrega_id", updatable = false)
    private UUID entregaId;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;

    public LinkAcompanhamento(UUID tenantId, Trajeto trajeto) {
        this.codigo = UUID.randomUUID();
        this.tenantId = tenantId;
        this.trajetoId = trajeto.getId();
        this.entregaId = trajeto.getEntregaId();
    }
}
