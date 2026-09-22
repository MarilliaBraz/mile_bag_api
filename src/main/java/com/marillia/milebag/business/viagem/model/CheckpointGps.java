package com.marillia.milebag.business.viagem.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Coordenadas e horário capturados num momento especifico da execucao -
 * imutável apos a criacao, igual a {@link CalculoRota}.
 */
@Entity
@Table(name = "checkpoint_gps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckpointGps extends BaseEntity {

    @Column(name = "trajeto_id", nullable = false, updatable = false)
    private UUID trajetoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoCheckpoint tipo;

    @Column(updatable = false)
    private Double lat;

    @Column(updatable = false)
    private Double lng;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Column(nullable = false, updatable = false)
    private boolean ausente;

    public CheckpointGps(UUID trajetoId, TipoCheckpoint tipo, Double lat, Double lng, Instant dataHora, boolean ausente) {
        this.trajetoId = trajetoId;
        this.tipo = tipo;
        this.lat = lat;
        this.lng = lng;
        this.dataHora = dataHora;
        this.ausente = ausente;
    }
}
