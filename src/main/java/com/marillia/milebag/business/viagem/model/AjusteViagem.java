package com.marillia.milebag.business.viagem.model;

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
 * Correcao pontual de um valor calculado de um {@link Trajeto} ja
 * consolidado (HU17, RD13, data-model.md desta HU) - registro append-only:
 * nunca atualizado nem apagado, sem setters. O valor vigente de um
 * {@code (viagemId, campo)} e sempre o {@code valorNovo} do registro mais
 * recente ({@code criadoEm}) desse par - ver {@code AjusteViagemService}.
 */
@Entity
@Table(name = "ajuste_viagem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AjusteViagem extends BaseEntity {

    @Column(name = "viagem_id", nullable = false, updatable = false)
    private UUID viagemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CampoAjusteViagem campo;

    @Column(name = "valor_anterior", nullable = false, updatable = false)
    private String valorAnterior;

    @Column(name = "valor_novo", nullable = false, updatable = false)
    private String valorNovo;

    @Column(nullable = false, updatable = false)
    private String justificativa;

    @Column(name = "motorista_id", nullable = false, updatable = false)
    private UUID motoristaId;

    public AjusteViagem(
            UUID viagemId, CampoAjusteViagem campo, String valorAnterior, String valorNovo, String justificativa, UUID motoristaId) {
        this.viagemId = viagemId;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
        this.justificativa = justificativa;
        this.motoristaId = motoristaId;
    }
}
