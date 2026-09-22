package com.marillia.milebag.business.ocorrencia.model;

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
 * Registro de uma tentativa de entrega malsucedida (data-model.md desta HU)
 * - append-only, nunca editado ou apagado (FR-004): sem setters, todos os
 * campos vem do construtor.
 */
@Entity
@Table(name = "ocorrencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ocorrencia extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "motivo_ocorrencia_id", nullable = false, updatable = false)
    private UUID motivoOcorrenciaId;

    @Column(name = "descricao_adicional", updatable = false)
    private String descricaoAdicional;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Column(name = "registrado_offline", nullable = false, updatable = false)
    private boolean registradoOffline;

    @Column(name = "id_local", updatable = false, length = 100)
    private String idLocal;

    public Ocorrencia(
            UUID entregaId,
            UUID motivoOcorrenciaId,
            String descricaoAdicional,
            Instant dataHora,
            boolean registradoOffline,
            String idLocal) {
        this.entregaId = entregaId;
        this.motivoOcorrenciaId = motivoOcorrenciaId;
        this.descricaoAdicional = descricaoAdicional;
        this.dataHora = dataHora;
        this.registradoOffline = registradoOffline;
        this.idLocal = idLocal;
    }
}
