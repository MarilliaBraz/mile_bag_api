package com.marillia.milebag.business.cancelamento.model;

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
 * Registro do cancelamento de um BDO pela empresa (V47) - um por BDO, append-only, como a dispensa e a ocorrencia.
 * Faz parte da rastreabilidade da mala.
 */
@Entity
@Table(name = "cancelamento_bdo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancelamentoBdo extends BaseEntity {

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "motivo_ocorrencia_id", nullable = false, updatable = false)
    private UUID motivoId;

    @Column(updatable = false)
    private String complemento;

    @Column(name = "cancelado_por", nullable = false, updatable = false)
    private UUID canceladoPor;

    /** Quem estava com a entrega no momento (avisado por push); nulo se ninguem tinha assumido. */
    @Column(name = "motorista_anterior_id", updatable = false)
    private UUID motoristaAnteriorId;

    /** O motorista ja estava em rota com a bagagem: a viagem foi encerrada, sem pagamento. */
    @Column(name = "em_rota", nullable = false, updatable = false)
    private boolean emRota;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public CancelamentoBdo(
            UUID bdoId, UUID entregaId, UUID motivoId, String complemento, UUID canceladoPor, UUID motoristaAnteriorId,
            boolean emRota, Instant dataHora) {
        this.bdoId = bdoId;
        this.entregaId = entregaId;
        this.motivoId = motivoId;
        this.complemento = complemento;
        this.canceladoPor = canceladoPor;
        this.motoristaAnteriorId = motoristaAnteriorId;
        this.emRota = emRota;
        this.dataHora = dataHora;
    }
}
