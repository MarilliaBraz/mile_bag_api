package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
 * Plano comercial da plataforma (HU16, RD11) - limites operacionais de
 * motoristas ativos e volume mensal de BDOs, associados a um tenant via
 * {@link HistoricoPlanoTenant} (nunca um campo direto em {@code Tenant} -
 * ver comentario da migracao V24). Sem faturamento/cobranca nesta HU
 * (spec.md, Assumptions).
 */
@Entity
@Table(name = "plano_assinatura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome;

    @Column(name = "limite_motoristas_ativos")
    private int limiteMotoristasAtivos;

    @Column(name = "limite_volume_mensal_bdos")
    private int limiteVolumeMensalBdos;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;

    public PlanoAssinatura(String nome, int limiteMotoristasAtivos, int limiteVolumeMensalBdos) {
        this.nome = nome;
        this.limiteMotoristasAtivos = limiteMotoristasAtivos;
        this.limiteVolumeMensalBdos = limiteVolumeMensalBdos;
    }
}
