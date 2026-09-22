package com.marillia.milebag.business.fechamento.model;

import java.math.BigDecimal;
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
 * Linha de detalhe de um fechamento (data-model.md desta HU) - append-only,
 * calculado uma vez na geracao (FR-007): sem setters, todos os campos vem
 * do construtor.
 */
@Entity
@Table(name = "item_fechamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemFechamento extends BaseEntity {

    @Column(name = "fechamento_id", nullable = false, updatable = false)
    private UUID fechamentoId;

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "motorista_id", nullable = false, updatable = false)
    private UUID motoristaId;

    @Column(name = "valor_pagamento_entrega", updatable = false, precision = 10, scale = 2)
    private BigDecimal valorPagamentoEntrega;

    @Column(name = "valor_reembolso_pedagio", updatable = false, precision = 10, scale = 2)
    private BigDecimal valorReembolsoPedagio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_item", nullable = false, updatable = false, length = 20)
    private StatusItemFechamento statusItem;

    @Column(name = "motivo_pendencia", updatable = false)
    private String motivoPendencia;

    public ItemFechamento(
            UUID fechamentoId,
            UUID entregaId,
            UUID motoristaId,
            BigDecimal valorPagamentoEntrega,
            BigDecimal valorReembolsoPedagio,
            StatusItemFechamento statusItem,
            String motivoPendencia) {
        this.fechamentoId = fechamentoId;
        this.entregaId = entregaId;
        this.motoristaId = motoristaId;
        this.valorPagamentoEntrega = valorPagamentoEntrega;
        this.valorReembolsoPedagio = valorReembolsoPedagio;
        this.statusItem = statusItem;
        this.motivoPendencia = motivoPendencia;
    }
}
