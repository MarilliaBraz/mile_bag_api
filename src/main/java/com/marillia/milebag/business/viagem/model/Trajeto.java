package com.marillia.milebag.business.viagem.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Trajeto real da viagem - agregado append-only (data-model.md desta HU): os
 * unicos campos que mudam depois da criacao sao os finais, e apenas uma vez,
 * na consolidacao ({@link #consolidar}) - por isso nao ha setters soltos.
 */
@Entity
@Table(name = "trajeto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trajeto extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrajetoStatus status;

    @Column(name = "data_inicio", nullable = false, updatable = false)
    private Instant dataInicio;

    @Column(name = "km_ida_final", precision = 10, scale = 3)
    private BigDecimal kmIdaFinal;

    @Column(name = "km_volta_final", precision = 10, scale = 3)
    private BigDecimal kmVoltaFinal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pracas_pedagio_final", columnDefinition = "jsonb")
    private String pracasPedagioFinal;

    @Column(name = "tempo_final")
    private Long tempoFinal;

    @Column(name = "justificativa_divergencia")
    private String justificativaDivergencia;

    public Trajeto(UUID entregaId, Instant dataInicio) {
        this.entregaId = entregaId;
        this.status = TrajetoStatus.EM_EXECUCAO;
        this.dataInicio = dataInicio;
    }

    /**
     * Unico ponto de escrita dos campos agregados (data-model.md: "so podem
     * ser preenchidos uma vez, no momento da consolidacao"). Chamado apenas
     * quando {@code status = EM_EXECUCAO} - reenviar apos {@code ENCERRADO}
     * e tratado como idempotente pelo chamador ({@code ViagemService}), que
     * simplesmente nao invoca este metodo de novo.
     */
    public void consolidar(
            BigDecimal kmIdaFinal, BigDecimal kmVoltaFinal, String pracasPedagioFinal, long tempoFinal, String justificativaDivergencia) {
        this.kmIdaFinal = kmIdaFinal;
        this.kmVoltaFinal = kmVoltaFinal;
        this.pracasPedagioFinal = pracasPedagioFinal;
        this.tempoFinal = tempoFinal;
        this.justificativaDivergencia = justificativaDivergencia;
        this.status = TrajetoStatus.ENCERRADO;
    }
}
