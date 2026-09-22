package com.marillia.milebag.business.entrega.bagagemcarona;

import java.math.BigDecimal;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bagagem adicional entregue na mesma viagem de uma entrega principal
 * (data-model.md desta HU) - sem endpoint de atualizacao (apenas criar e
 * remover o vinculo), por isso sem setters: todos os campos vem do
 * construtor.
 */
@Entity
@Table(name = "bagagem_carona")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagagemCarona extends BaseEntity {

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "entrega_principal_id", nullable = false, updatable = false)
    private UUID entregaPrincipalId;

    /**
     * Calculada e congelada no momento da vinculacao (RN03, research.md) -
     * nunca recalculada, nem mesmo quando a rota da entrega principal e
     * recalculada livremente (HU04).
     */
    @Column(name = "quilometragem_complementar", nullable = false, updatable = false, precision = 10, scale = 3)
    private BigDecimal quilometragemComplementar;

    public BagagemCarona(UUID bdoId, UUID entregaPrincipalId, BigDecimal quilometragemComplementar) {
        this.bdoId = bdoId;
        this.entregaPrincipalId = entregaPrincipalId;
        this.quilometragemComplementar = quilometragemComplementar;
    }
}
