package com.marillia.milebag.business.encaminhamento.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.marillia.milebag.core.base.BaseEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V65: um custo de um processo de bagagem, lancado pela companhia (RLS da companhia). */
@Entity
@Table(name = "custo_processo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustoProcesso extends BaseEntity {

    @Column(name = "bagagem_id", nullable = false, updatable = false)
    private UUID bagagemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoCusto tipo;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(length = 200, updatable = false)
    private String descricao;

    @Column(nullable = false, updatable = false)
    private LocalDate data;

    @Column(name = "lancado_por", nullable = false, updatable = false)
    private UUID lancadoPor;

    public CustoProcesso(UUID bagagemId, TipoCusto tipo, BigDecimal valor, String descricao, LocalDate data, UUID lancadoPor) {
        this.bagagemId = bagagemId;
        this.tipo = tipo;
        this.valor = valor;
        this.descricao = descricao;
        this.data = data;
        this.lancadoPor = lancadoPor;
    }
}
