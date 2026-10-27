package com.marillia.milebag.business.sincronizacao.model;

import java.time.Instant;
import java.util.UUID;

/** Adaptacao de {@code POST /ocorrencias} (HU07): {@code entregaId} no payload. */
public record PayloadOcorrenciaRegistrada(
        UUID entregaId, UUID motivoOcorrenciaId, String descricaoAdicional, Instant janelaInicio, Instant janelaFim) {
}
