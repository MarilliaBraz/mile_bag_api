package com.marillia.milebag.business.emergencia.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;
import com.marillia.milebag.core.exception.ConflitoException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Alerta de emergencia acionado por um motorista (RF23, RD16, HU30). Nunca
 * apagado; {@link #cancelar} e {@link #atender} sao os unicos pontos de
 * mutacao e so valem a partir de {@code ABERTO}.
 */
@Entity
@Table(name = "alerta_emergencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaEmergencia extends BaseEntity {

    @Column(name = "motorista_id", nullable = false, updatable = false)
    private UUID motoristaId;

    @Column(name = "entrega_id", updatable = false)
    private UUID entregaId;

    @Column(updatable = false)
    private Double latitude;

    @Column(updatable = false)
    private Double longitude;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAlertaEmergencia status;

    @Column
    private String observacao;

    @Column(name = "encerrado_por")
    private UUID encerradoPor;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    public AlertaEmergencia(UUID motoristaId, UUID entregaId, Double latitude, Double longitude, Instant dataHora) {
        this.motoristaId = motoristaId;
        this.entregaId = entregaId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dataHora = dataHora;
        this.status = StatusAlertaEmergencia.ABERTO;
    }

    public boolean hasPosicao() {
        return latitude != null && longitude != null;
    }

    /** Pelo proprio motorista, acionado por engano. */
    public void cancelar(UUID motoristaId, Instant quando) {
        encerrar(StatusAlertaEmergencia.CANCELADO, motoristaId, null, quando);
    }

    /** Pelo administrador do tenant. */
    public void atender(UUID administradorId, String observacao, Instant quando) {
        encerrar(StatusAlertaEmergencia.ATENDIDO, administradorId, observacao, quando);
    }

    private void encerrar(StatusAlertaEmergencia novoStatus, UUID autor, String observacao, Instant quando) {
        if (status != StatusAlertaEmergencia.ABERTO) {
            throw new ConflitoException("Este alerta ja foi encerrado.");
        }
        this.status = novoStatus;
        this.encerradoPor = autor;
        this.observacao = observacao == null || observacao.isBlank() ? null : observacao.trim();
        this.encerradoEm = quando;
    }
}
