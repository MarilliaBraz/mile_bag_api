package com.marillia.milebag.business.comprovante.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.business.entrega.model.EntregaStatus;

/** Ver contracts/comprovante-entrega-api.md. */
public record ComprovanteEntregaResponse(UUID id, UUID entregaId, Instant dataHora, EntregaStatus entregaStatus) {

    public static ComprovanteEntregaResponse de(ComprovanteEntrega comprovante) {
        return new ComprovanteEntregaResponse(
                comprovante.getId(), comprovante.getEntregaId(), comprovante.getDataHora(), EntregaStatus.ENCERRADA_COM_SUCESSO);
    }
}
