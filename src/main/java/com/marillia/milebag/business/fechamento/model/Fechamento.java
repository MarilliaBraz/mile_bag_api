package com.marillia.milebag.business.fechamento.model;

import java.time.Instant;
import java.time.LocalDate;

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
 * Apuracao periodica de pagamento (RD10, data-model.md desta HU) - unica
 * por (tenant, periodo), garantida por constraint no banco (FR-005,
 * research.md). {@code status} e o unico campo que muda apos a criacao,
 * quando {@link #atualizarStatus} e chamado ao final da geracao (so entao
 * se sabe se algum item ficou pendente).
 */
@Entity
@Table(name = "fechamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fechamento extends BaseEntity {

    @Column(name = "periodo_inicio", nullable = false, updatable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim", nullable = false, updatable = false)
    private LocalDate periodoFim;

    @Column(name = "gerado_em", nullable = false, updatable = false)
    private Instant geradoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusFechamento status;

    public Fechamento(LocalDate periodoInicio, LocalDate periodoFim) {
        this.periodoInicio = periodoInicio;
        this.periodoFim = periodoFim;
        this.geradoEm = Instant.now();
        this.status = StatusFechamento.COMPLETO;
    }

    public void atualizarStatus(StatusFechamento status) {
        this.status = status;
    }
}
