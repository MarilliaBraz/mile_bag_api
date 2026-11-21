package com.marillia.milebag.business.fotoentrega.model;

import java.time.Instant;
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

/** Uma foto da entrega (V48): de onde veio (retirada ou comprovante) e o caminho no armazenamento. Nunca editada. */
@Entity
@Table(name = "foto_entrega")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FotoEntrega extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoFotoEntrega tipo;

    @Column(name = "retirada_id", updatable = false)
    private UUID retiradaId;

    @Column(name = "comprovante_id", updatable = false)
    private UUID comprovanteId;

    @Column(nullable = false, updatable = false, length = 500)
    private String caminho;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public FotoEntrega(UUID entregaId, TipoFotoEntrega tipo, UUID retiradaId, UUID comprovanteId, String caminho, Instant dataHora) {
        this.entregaId = entregaId;
        this.tipo = tipo;
        this.retiradaId = retiradaId;
        this.comprovanteId = comprovanteId;
        this.caminho = caminho;
        this.dataHora = dataHora;
    }
}
