package com.marillia.milebag.business.sincronizacao.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Ver contracts/sincronizacao-api.md. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultadoEventoResponse(UUID idLocal, ResultadoSincronizacao resultado, UUID idServidor, String motivo) {
}
