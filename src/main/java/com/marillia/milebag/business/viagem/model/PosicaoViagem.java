package com.marillia.milebag.business.viagem.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Item 9 (V55): um ponto do caminho do motorista durante a viagem - so entra, nunca e alterado nem apagado. */
@Entity
@Table(name = "posicao_viagem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PosicaoViagem extends BaseEntity {

    @Column(name = "trajeto_id", nullable = false, updatable = false)
    private UUID trajetoId;

    @Column(nullable = false, updatable = false)
    private double latitude;

    @Column(nullable = false, updatable = false)
    private double longitude;

    @Column(name = "precisao_metros", updatable = false)
    private Double precisaoMetros;

    /** Hora no celular, quando o ponto foi lido (o envio pode ser bem depois, sem internet). */
    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    /** Gerado no celular: o mesmo ponto reenviado nao entra duas vezes. */
    @Column(name = "id_local", nullable = false, updatable = false, length = 64)
    private String idLocal;

    public PosicaoViagem(UUID trajetoId, double latitude, double longitude, Double precisaoMetros, Instant dataHora, String idLocal) {
        this.trajetoId = trajetoId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.precisaoMetros = precisaoMetros;
        this.dataHora = dataHora;
        this.idLocal = idLocal;
    }
}
