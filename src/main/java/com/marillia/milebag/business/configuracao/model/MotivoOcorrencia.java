package com.marillia.milebag.business.configuracao.model;

import java.util.UUID;

import com.marillia.milebag.core.config.security.TenantContext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Item do catalogo de motivos de ocorrencia por tenant (RD07, data-model.md
 * desta HU) - consumido em business/ocorrencia (HU07). Nao extende
 * {@link com.marillia.milebag.core.base.BaseEntity} pelas mesmas razoes de
 * {@code MotivoOcorrenciaValor} de HU10/HU09 nao se aplicarem aqui: e um
 * catalogo simples, sem necessidade dos campos de auditoria dela.
 *
 * <p>Nunca excluida (FR-009) - {@link #desativar()}/{@link #ativar()} sao os
 * unicos pontos de mutacao depois da criacao.</p>
 */
@Entity
@Table(name = "motivo_ocorrencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotivoOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false, length = 200)
    private String descricao;

    @Column(nullable = false)
    private boolean ativo;

    public MotivoOcorrencia(String descricao) {
        this.descricao = descricao;
        this.ativo = true;
    }

    public void definirAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    @PrePersist
    private void preencherTenantAntesDePersistir() {
        if (tenantId == null) {
            tenantId = TenantContext.get();
        }
    }
}
