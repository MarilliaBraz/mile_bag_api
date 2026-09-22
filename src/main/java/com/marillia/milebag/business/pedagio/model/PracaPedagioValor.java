package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Um valor historico de uma praca (data-model.md desta HU) - append-only,
 * nunca editado ou apagado (FR-002/research.md): sem setters, todos os
 * campos vem do construtor.
 */
@Entity
@Table(name = "praca_pedagio_valor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracaPedagioValor extends BaseEntity {

    @Column(name = "praca_pedagio_id", nullable = false, updatable = false)
    private UUID pracaPedagioId;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private LocalDate vigenteDesde;

    public PracaPedagioValor(UUID pracaPedagioId, BigDecimal valor, LocalDate vigenteDesde) {
        this.pracaPedagioId = pracaPedagioId;
        this.valor = valor;
        this.vigenteDesde = vigenteDesde;
    }
}
