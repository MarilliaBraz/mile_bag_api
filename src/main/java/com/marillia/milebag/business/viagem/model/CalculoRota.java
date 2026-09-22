package com.marillia.milebag.business.viagem.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.marillia.milebag.business.rota.model.TipoSelecao;
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
 * Um cálculo (ou recálculo) de rota já utilizado pelo motorista durante a
 * execução da viagem (HU03) - imutável após a criação (FR-004): sem
 * setters, todos os campos vêm do construtor.
 */
@Entity
@Table(name = "calculo_rota")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalculoRota extends BaseEntity {

    @Column(name = "trajeto_id", nullable = false, updatable = false)
    private UUID trajetoId;

    @Column(name = "km_ida", nullable = false, updatable = false, precision = 10, scale = 3)
    private BigDecimal kmIda;

    @Column(name = "km_volta", nullable = false, updatable = false, precision = 10, scale = 3)
    private BigDecimal kmVolta;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pracas_pedagio", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String pracasPedagio;

    @Column(name = "tempo_estimado", nullable = false, updatable = false)
    private long tempoEstimado;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_selecao", nullable = false, updatable = false, length = 20)
    private TipoSelecao origemSelecao;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Column(name = "eh_rota_inicial", nullable = false, updatable = false)
    private boolean ehRotaInicial;

    public CalculoRota(
            UUID trajetoId,
            BigDecimal kmIda,
            BigDecimal kmVolta,
            String pracasPedagio,
            long tempoEstimado,
            TipoSelecao origemSelecao,
            Instant dataHora,
            boolean ehRotaInicial) {
        this.trajetoId = trajetoId;
        this.kmIda = kmIda;
        this.kmVolta = kmVolta;
        this.pracasPedagio = pracasPedagio;
        this.tempoEstimado = tempoEstimado;
        this.origemSelecao = origemSelecao;
        this.dataHora = dataHora;
        this.ehRotaInicial = ehRotaInicial;
    }
}
