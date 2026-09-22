package com.marillia.milebag.business.sincronizacao.model;

import java.util.UUID;

/** Adaptacao de {@code POST /bagagens-carona} (HU05): ambos os ids podem referenciar eventos anteriores do mesmo lote. */
public record PayloadBagagemCaronaVinculada(UUID entregaId, UUID bdoId) {
}
