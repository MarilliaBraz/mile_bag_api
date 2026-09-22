package com.marillia.milebag.business.ocorrencia.model;

import java.util.UUID;

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
 * Item do catalogo de motivos de ocorrencia por tenant (data-model.md desta
 * HU). Somente leitura aqui - CRUD completo pertence a HU11; por isso nao
 * extende {@link com.marillia.milebag.core.base.BaseEntity} (que sempre
 * mapeia campos de auditoria que este catalogo nao usa) nem tem construtor
 * de negocio - as linhas vem do cadastro de HU11 (ou de um seed de
 * desenvolvimento, ver migracao V13).
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
    private String nome;

    @Column(nullable = false)
    private boolean ativo;
}
