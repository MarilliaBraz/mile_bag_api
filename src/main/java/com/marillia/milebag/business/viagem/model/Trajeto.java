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

    /** Quem dirigiu (V37): a entrega reaberta por ocorrencia fica sem motorista, mas a viagem continua sendo dele. */
    @Column(name = "motorista_id", updatable = false)
    private UUID motoristaId;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    /**
     * Ocorrencia que encerrou esta viagem (HU07): uma tentativa sem sucesso. A viagem fica no historico, paga
     * a quem dirigiu, e deixa de ser a viagem da tentativa atual - a nova tentativa comeca uma viagem nova.
     */
    @Column(name = "ocorrencia_id")
    private UUID ocorrenciaId;

    /**
     * Ultima posicao do motorista na tentativa em andamento (HU31, RN16): substituida a cada envio do app e
     * apagada ao encerrar - excecao a "append-only" acima, porque nao e dado da viagem, so do acompanhamento.
     */
    @Column(name = "posicao_latitude")
    private Double posicaoLatitude;

    @Column(name = "posicao_longitude")
    private Double posicaoLongitude;

    @Column(name = "posicao_registrada_em")
    private Instant posicaoRegistradaEm;

    public Trajeto(UUID entregaId, UUID motoristaId, Instant dataInicio) {
        this.entregaId = entregaId;
        this.motoristaId = motoristaId;
        this.status = TrajetoStatus.EM_EXECUCAO;
        this.dataInicio = dataInicio;
    }

    /** Tentativa sem sucesso (HU07): a viagem ja encerrada (ou encerrada agora, por quem chama) passa a pertencer a ocorrencia. */
    public void vincularOcorrencia(UUID ocorrenciaId, Instant quando) {
        this.ocorrenciaId = ocorrenciaId;
        if (this.encerradoEm == null) {
            this.encerradoEm = quando;
        }
        descartarPosicao();
    }

    public void atualizarPosicao(double latitude, double longitude, Instant quando) {
        this.posicaoLatitude = latitude;
        this.posicaoLongitude = longitude;
        this.posicaoRegistradaEm = quando;
    }

    public boolean temPosicao() {
        return posicaoRegistradaEm != null;
    }

    private void descartarPosicao() {
        this.posicaoLatitude = null;
        this.posicaoLongitude = null;
        this.posicaoRegistradaEm = null;
    }

    public boolean encerradoPorOcorrencia() {
        return ocorrenciaId != null;
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
        this.encerradoEm = Instant.now();
        descartarPosicao();
    }
}
