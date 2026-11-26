package com.marillia.milebag.business.pagamento.model;

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

/**
 * Pagamento da quinzena de um motorista num fechamento (V60), marcado pela empresa conforme for pagando. Uma linha por
 * fechamento e motorista; sem linha, o pagamento esta a agendar. Cada mudanca deixa um EventoPagamentoMotorista.
 */
@Entity
@Table(name = "pagamento_motorista")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PagamentoMotorista extends BaseEntity {

    @Column(name = "fechamento_id", nullable = false, updatable = false)
    private UUID fechamentoId;

    @Column(name = "motorista_id", nullable = false, updatable = false)
    private UUID motoristaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoPagamento situacao;

    @Column(name = "data_agendada")
    private LocalDate dataAgendada;

    @Column(name = "pago_em")
    private LocalDate pagoEm;

    @Column(name = "atualizado_por", nullable = false)
    private UUID atualizadoPor;

    public PagamentoMotorista(UUID fechamentoId, UUID motoristaId, UUID usuarioId) {
        this.fechamentoId = fechamentoId;
        this.motoristaId = motoristaId;
        this.situacao = SituacaoPagamento.A_AGENDAR;
        this.atualizadoPor = usuarioId;
    }

    public void agendar(LocalDate data, UUID usuarioId) {
        this.situacao = SituacaoPagamento.AGENDADO;
        this.dataAgendada = data;
        this.atualizadoPor = usuarioId;
    }

    public void marcarPago(LocalDate data, UUID usuarioId) {
        this.situacao = SituacaoPagamento.PAGO;
        this.pagoEm = data;
        this.atualizadoPor = usuarioId;
    }

    /** Pago marcado por engano: volta para agendado (se havia data) ou a agendar. */
    public void desfazerPago(UUID usuarioId) {
        this.situacao = dataAgendada != null ? SituacaoPagamento.AGENDADO : SituacaoPagamento.A_AGENDAR;
        this.pagoEm = null;
        this.atualizadoPor = usuarioId;
    }
}
