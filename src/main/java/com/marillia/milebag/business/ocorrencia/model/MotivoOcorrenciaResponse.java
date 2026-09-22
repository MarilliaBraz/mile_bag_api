package com.marillia.milebag.business.ocorrencia.model;

import java.util.UUID;

/** Ver contracts/ocorrencia-api.md. */
public record MotivoOcorrenciaResponse(UUID id, String nome) {

    public static MotivoOcorrenciaResponse de(MotivoOcorrencia motivo) {
        return new MotivoOcorrenciaResponse(motivo.getId(), motivo.getNome());
    }
}
