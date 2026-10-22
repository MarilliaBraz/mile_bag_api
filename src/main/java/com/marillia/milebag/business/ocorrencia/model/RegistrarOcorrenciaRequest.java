package com.marillia.milebag.business.ocorrencia.model;

import java.time.Instant;
import java.util.UUID;

/** {@code janelaInicio}/{@code janelaFim}: obrigatorios para motivo de reagendamento, ausentes nos demais (HU29). */
public record RegistrarOcorrenciaRequest(
        UUID motivoOcorrenciaId, String descricaoAdicional, String idLocal, Instant janelaInicio, Instant janelaFim) {
}
