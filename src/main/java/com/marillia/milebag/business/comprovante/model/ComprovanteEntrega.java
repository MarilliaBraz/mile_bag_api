package com.marillia.milebag.business.comprovante.model;

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
 * Comprovante de entrega (POD, data-model.md desta HU) - registro que
 * encerra a entrega com sucesso (RN04/RD06). Sem endpoint de atualizacao,
 * por isso sem setters: todos os campos vem do construtor.
 */
@Entity
@Table(name = "comprovante_entrega")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComprovanteEntrega extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "foto_documento_url", nullable = false, updatable = false, length = 500)
    private String fotoDocumentoUrl;

    @Column(name = "identificacao_recebedor", nullable = false, updatable = false, length = 200)
    private String identificacaoRecebedor;

    /** Momento da captura no dispositivo, nao do envio ao servidor (data-model.md). */
    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Column(nullable = false, updatable = false)
    private double latitude;

    @Column(nullable = false, updatable = false)
    private double longitude;

    @Column(name = "registrado_offline", nullable = false, updatable = false)
    private boolean registradoOffline;

    @Column(name = "id_local", updatable = false, length = 100)
    private String idLocal;

    public ComprovanteEntrega(
            UUID entregaId,
            String fotoDocumentoUrl,
            String identificacaoRecebedor,
            Instant dataHora,
            double latitude,
            double longitude,
            boolean registradoOffline,
            String idLocal) {
        this.entregaId = entregaId;
        this.fotoDocumentoUrl = fotoDocumentoUrl;
        this.identificacaoRecebedor = identificacaoRecebedor;
        this.dataHora = dataHora;
        this.latitude = latitude;
        this.longitude = longitude;
        this.registradoOffline = registradoOffline;
        this.idLocal = idLocal;
    }
}
