package com.marillia.milebag.business.tenant.model;

import java.time.Instant;
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
 * Qual {@link PlanoAssinatura} vigorou para um tenant em cada intervalo de
 * tempo (HU16, data-model.md) - UNICA fonte de verdade do plano vigente de
 * um tenant (tasks.md T016; {@code Tenant} nao guarda {@code planoId}, ver
 * migracao V24). No maximo um registro por tenant tem {@code vigenteAte =
 * null} (o vigente) - garantido por indice unico parcial na migracao.
 */
@Entity
@Table(name = "historico_plano_tenant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoricoPlanoTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "plano_id", updatable = false)
    private UUID planoId;

    @Column(name = "vigente_desde", updatable = false)
    private Instant vigenteDesde;

    @Column(name = "vigente_ate")
    private Instant vigenteAte;

    @Column(name = "uso_excedeu_limite_na_troca", updatable = false)
    private boolean usoExcedeuLimiteNaTroca;

    public HistoricoPlanoTenant(UUID tenantId, UUID planoId, Instant vigenteDesde, boolean usoExcedeuLimiteNaTroca) {
        this.tenantId = tenantId;
        this.planoId = planoId;
        this.vigenteDesde = vigenteDesde;
        this.usoExcedeuLimiteNaTroca = usoExcedeuLimiteNaTroca;
    }

    /** Fecha este registro no momento em que um novo plano passa a vigorar (FR-004). */
    public void encerrar(Instant agora) {
        this.vigenteAte = agora;
    }
}
