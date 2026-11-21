package com.marillia.milebag.business.segundavia.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Um envio da segunda via do BDO por e-mail (V50) - so o registro; o PDF nunca e guardado. Nunca editado. */
@Entity
@Table(name = "envio_segunda_via_bdo")
@Getter
@NoArgsConstructor
public class EnvioSegundaViaBdo extends BaseEntity {

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "enviado_por", nullable = false, updatable = false)
    private UUID enviadoPor;

    @Column(nullable = false, updatable = false, length = 200)
    private String destinatario;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public EnvioSegundaViaBdo(UUID bdoId, UUID enviadoPor, String destinatario, Instant dataHora) {
        this.bdoId = bdoId;
        this.enviadoPor = enviadoPor;
        this.destinatario = destinatario;
        this.dataHora = dataHora;
    }
}
