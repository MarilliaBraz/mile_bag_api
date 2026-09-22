package com.marillia.milebag.core.base;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.marillia.milebag.core.config.security.TenantContext;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclasse de toda entidade de negocio do Milebag.
 *
 * <p>Toda entidade carrega {@code tenantId}: e o discriminador usado pela
 * politica de Row Level Security no PostgreSQL (isolamento RNF01). O valor e
 * sempre resolvido a partir do token JWT da requisicao (ver
 * {@code core.config.security}), nunca recebido como parametro vindo do
 * cliente - ver {@code research.md} da feature 001, secao 3.</p>
 */
@Getter
@Setter
@MappedSuperclass
@EqualsAndHashCode(of = "id")
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    /**
     * Preenche o tenant automaticamente a partir da requisicao atual, nunca
     * exigindo que um {@code Service} lembre de fazer isso manualmente -
     * mesma razao de {@code TenantAspect} ser automatico via AOP (RNF01). Sem
     * isso, o INSERT falharia a policy de RLS ({@code WITH CHECK}) sempre que
     * um {@code Service} esquecesse de atribuir o tenant antes de salvar.
     */
    @PrePersist
    private void preencherTenantAntesDePersistir() {
        if (tenantId == null) {
            tenantId = TenantContext.get();
        }
    }
}
