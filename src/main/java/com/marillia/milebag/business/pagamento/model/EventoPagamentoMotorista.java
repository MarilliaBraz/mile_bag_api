package com.marillia.milebag.business.pagamento.model;

import java.time.Instant;
import java.time.LocalDate;
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

/** Rastro do pagamento (V60): agendou para, pagou em, desfez o pago com motivo - quem e quando. Nunca editado. */
@Entity
@Table(name = "evento_pagamento_motorista")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoPagamentoMotorista extends BaseEntity {

    @Column(name = "pagamento_motorista_id", nullable = false, updatable = false)
    private UUID pagamentoMotoristaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoEventoPagamento tipo;

    @Column(name = "data_referencia", updatable = false)
    private LocalDate dataReferencia;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public EventoPagamentoMotorista(UUID pagamentoMotoristaId, TipoEventoPagamento tipo, LocalDate dataReferencia, String motivo,
            UUID usuarioId, Instant dataHora) {
        this.pagamentoMotoristaId = pagamentoMotoristaId;
        this.tipo = tipo;
        this.dataReferencia = dataReferencia;
        this.motivo = motivo;
        this.usuarioId = usuarioId;
        this.dataHora = dataHora;
    }
}
