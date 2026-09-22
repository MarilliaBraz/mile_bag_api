package com.marillia.milebag.business.configuracao.model;

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
 * Historico de vigencia da periodicidade de fechamento do tenant
 * (data-model.md desta HU) - mesmo padrao append-only de
 * {@code PracaPedagioValor} (HU10) e {@code TabelaTarifa} (HU09).
 */
@Entity
@Table(name = "periodicidade_fechamento_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeriodicidadeFechamentoConfig extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Periodicidade periodicidade;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private LocalDate vigenteDesde;

    public PeriodicidadeFechamentoConfig(Periodicidade periodicidade, LocalDate vigenteDesde) {
        this.periodicidade = periodicidade;
        this.vigenteDesde = vigenteDesde;
    }
}
