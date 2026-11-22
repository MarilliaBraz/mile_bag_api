package com.marillia.milebag.business.basebdo.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A empresa trocou a base de um BDO ainda nao assumido (V52), com motivo - historico; nunca editada. */
@Entity
@Table(name = "troca_base_bdo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrocaBaseBdo extends BaseEntity {

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "base_anterior_id", updatable = false)
    private UUID baseAnteriorId;

    @Column(name = "base_nova_id", nullable = false, updatable = false)
    private UUID baseNovaId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "alterado_por", nullable = false, updatable = false)
    private UUID alteradoPor;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public TrocaBaseBdo(UUID bdoId, UUID baseAnteriorId, UUID baseNovaId, String motivo, UUID alteradoPor, Instant dataHora) {
        this.bdoId = bdoId;
        this.baseAnteriorId = baseAnteriorId;
        this.baseNovaId = baseNovaId;
        this.motivo = motivo;
        this.alteradoPor = alteradoPor;
        this.dataHora = dataHora;
    }
}
