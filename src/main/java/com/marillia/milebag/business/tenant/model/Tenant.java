package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Empresa assinante do sistema (HU14) - diferente de toda entidade de
 * dominio ja existente, NAO estende {@code BaseEntity}: {@code id} aqui E o
 * proprio identificador de tenant usado como discriminador de RLS em todas
 * as outras tabelas, nao uma referencia a ele - nao faz sentido esta linha
 * ter, ela mesma, um {@code tenantId} apontando para si. Ver V21 (migracao)
 * para a decisao de nao ter Row Level Security nesta tabela.
 */
@Entity
@Table(name = "tenant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identificador_empresa", updatable = false, length = 32)
    private String identificadorEmpresa;

    @Column(length = 200)
    private String nome;

    @Column(name = "plano_id", updatable = false)
    private UUID planoId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusTenant status;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;

    public Tenant(String identificadorEmpresa, String nome, UUID planoId) {
        this.identificadorEmpresa = identificadorEmpresa;
        this.nome = nome;
        this.planoId = planoId;
        this.status = StatusTenant.ATIVO;
    }
}
