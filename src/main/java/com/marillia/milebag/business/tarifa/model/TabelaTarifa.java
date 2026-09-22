package com.marillia.milebag.business.tarifa.model;

import java.math.BigDecimal;
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
 * Tarifa paga por entrega (RD09, data-model.md desta HU) - identidade
 * (escopo/motoristaId/regiao) fixada na criacao; so os valores e a
 * vigencia podem mudar depois ({@link #atualizarValores}), conforme
 * contracts/tarifas-api.md (PUT so aceita "os mesmos campos de valor do
 * POST").
 */
@Entity
@Table(name = "tabela_tarifa")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TabelaTarifa extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EscopoTarifa escopo;

    @Column(name = "motorista_id", updatable = false)
    private UUID motoristaId;

    @Column(updatable = false, length = 200)
    private String regiao;

    @Column(name = "valor_fixo", precision = 10, scale = 2)
    private BigDecimal valorFixo;

    @Column(name = "valor_por_km", precision = 10, scale = 3)
    private BigDecimal valorPorKm;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    public TabelaTarifa(
            EscopoTarifa escopo, UUID motoristaId, String regiao, BigDecimal valorFixo, BigDecimal valorPorKm, LocalDate vigenteDesde) {
        this.escopo = escopo;
        this.motoristaId = motoristaId;
        this.regiao = regiao;
        this.valorFixo = valorFixo;
        this.valorPorKm = valorPorKm;
        this.vigenteDesde = vigenteDesde;
    }

    public void atualizarValores(BigDecimal valorFixo, BigDecimal valorPorKm, LocalDate vigenteDesde) {
        this.valorFixo = valorFixo;
        this.valorPorKm = valorPorKm;
        this.vigenteDesde = vigenteDesde;
    }
}
