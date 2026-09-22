package com.marillia.milebag.business.entrega.model;

import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Liga um BDO confirmado (de qualquer origem) a um motorista responsavel -
 * ver data-model.md desta HU.
 */
@Entity
@Table(name = "entrega")
@Getter
@Setter
@NoArgsConstructor
public class Entrega extends BaseEntity {

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "motorista_responsavel")
    private UUID motoristaResponsavel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EntregaStatus status = EntregaStatus.AGUARDANDO_ATRIBUICAO;
}
