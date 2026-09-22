package com.marillia.milebag.business.sincronizacao.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Um item do lote de {@code POST /api/v1/sync/eventos} (contracts/sincronizacao-api.md).
 * {@code payload} e generico aqui - o formato especifico de cada
 * {@code tipoEvento} e resolvido por {@code SincronizacaoEventoDispatcher}.
 */
public record EventoSincronizacaoRequest(
        UUID idLocal, long sequenciaLocal, TipoEventoSincronizacao tipoEvento, Instant timestampCriacaoLocal, JsonNode payload) {
}
