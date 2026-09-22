package com.marillia.milebag.business.sincronizacao.model;

import java.util.UUID;

/** Adaptacao de {@code POST /viagem/iniciar} (HU04): {@code entregaId} vai no payload. */
public record PayloadViagemIniciada(UUID entregaId, Double lat, Double lng) {
}
