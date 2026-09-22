package com.marillia.milebag.business.sincronizacao.model;

import java.util.UUID;

/** Ver contracts/sincronizacao-api.md, GET /sync/status. */
public record StatusEventoResponse(UUID idLocal, ResultadoSincronizacao resultado) {
}
