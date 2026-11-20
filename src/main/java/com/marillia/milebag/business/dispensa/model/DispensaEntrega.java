package com.marillia.milebag.business.dispensa.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de uma dispensa: o motorista devolveu a entrega aos outros motoristas (V46). Faz parte da
 * rastreabilidade da mala - append-only, nunca editado nem apagado, como a ocorrencia.
 */
@Entity
@Table(name = "dispensa_entrega")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DispensaEntrega extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "motorista_id", nullable = false, updatable = false)
    private UUID motoristaId;

    /** Nulo so quando o motivo nao era exigido: primeira tentativa, bagagem ainda nao retirada. */
    @Column(name = "motivo_ocorrencia_id", updatable = false)
    private UUID motivoId;

    @Column(updatable = false)
    private String complemento;

    /** O motorista ja estava em rota com a bagagem: a viagem foi encerrada na dispensa (e e paga a ele). */
    @Column(name = "com_bagagem", nullable = false, updatable = false)
    private boolean comBagagem;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public DispensaEntrega(UUID entregaId, UUID motoristaId, UUID motivoId, String complemento, boolean comBagagem, Instant dataHora) {
        this.entregaId = entregaId;
        this.motoristaId = motoristaId;
        this.motivoId = motivoId;
        this.complemento = complemento;
        this.comBagagem = comBagagem;
        this.dataHora = dataHora;
    }
}
