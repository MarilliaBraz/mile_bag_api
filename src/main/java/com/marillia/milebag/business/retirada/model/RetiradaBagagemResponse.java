package com.marillia.milebag.business.retirada.model;

import java.time.Instant;
import java.util.UUID;

/** Retirada registrada (V48). {@code comLocalizacao}: o aparelho deu a posição no momento. */
public record RetiradaBagagemResponse(UUID id, UUID entregaId, Instant dataHora, boolean comLocalizacao, String observacaoAvaria) {

    public static RetiradaBagagemResponse de(RetiradaBagagem retirada) {
        return new RetiradaBagagemResponse(
                retirada.getId(), retirada.getEntregaId(), retirada.getDataHora(), retirada.getLatitude() != null, retirada.getObservacaoAvaria());
    }
}
