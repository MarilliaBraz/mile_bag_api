package com.marillia.milebag.business.configuracao.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Identidade visual do tenant (HU13) - no maximo um registro por tenant
 * (RI07, reforcado por indice unico em tenant_id na migracao). {@code
 * atualizadoEm} vem de {@link BaseEntity} (ja atualizado automaticamente a
 * cada save via @UpdateTimestamp) - dispensa campo proprio (data-model.md).
 */
@Entity
@Table(name = "identidade_visual_tenant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentidadeVisualTenant extends BaseEntity {

    @Column(name = "logotipo_path")
    private String logotipoPath;

    @Column(length = 200)
    private String cabecalho;

    public IdentidadeVisualTenant(String logotipoPath, String cabecalho) {
        this.logotipoPath = logotipoPath;
        this.cabecalho = cabecalho;
    }

    /** PUT sempre substitui integralmente (contracts/identidade-visual-api.md: "cria ou substitui"). */
    public void substituir(String logotipoPath, String cabecalho) {
        this.logotipoPath = logotipoPath;
        this.cabecalho = cabecalho;
    }
}
