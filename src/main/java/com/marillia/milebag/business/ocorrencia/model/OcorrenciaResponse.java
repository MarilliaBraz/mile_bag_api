package com.marillia.milebag.business.ocorrencia.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.business.entrega.model.EntregaStatus;

/**
 * Ver contracts/ocorrencia-api.md. {@code entregaStatus} reflete o status
 * atual da entrega no momento da consulta - o mesmo valor em todo o
 * historico retornado por {@code GET}, ja que o dominio nao guarda um
 * status "congelado" por ocorrencia individual.
 */
public record OcorrenciaResponse(
        UUID id, UUID entregaId, UUID motivoOcorrenciaId, Instant dataHora, EntregaStatus entregaStatus) {
}
