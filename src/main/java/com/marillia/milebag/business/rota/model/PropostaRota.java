package com.marillia.milebag.business.rota.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.marillia.milebag.core.config.security.TenantContext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Proposta de rota transitoria de uma entrega (data-model.md desta HU) - a
 * chave primaria e o proprio {@code entregaId} (nao um id gerado): cada novo
 * calculo substitui o anterior enquanto a viagem nao foi iniciada (HU04),
 * por isso nao extende {@link com.marillia.milebag.core.base.BaseEntity}
 * (cujo id e sempre gerado). O preenchimento automatico do tenant e
 * replicado aqui pela mesma razao ja documentada la.
 */
@Entity
@Table(name = "proposta_rota")
@Getter
@Setter
@NoArgsConstructor
public class PropostaRota {

    @Id
    @Column(name = "entrega_id")
    private UUID entregaId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String origem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String destino;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String alternativas;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_selecao", length = 20)
    private TipoSelecao tipoSelecao;

    private String justificativa;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    private void preencherTenantAntesDePersistir() {
        if (tenantId == null) {
            tenantId = TenantContext.get();
        }
    }
}
