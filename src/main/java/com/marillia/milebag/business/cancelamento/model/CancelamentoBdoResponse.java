package com.marillia.milebag.business.cancelamento.model;

import java.time.Instant;
import java.util.UUID;

/** O cancelamento de um BDO, para a empresa (V47). {@code motoristaAnteriorNome} nulo: ninguem tinha assumido. */
public record CancelamentoBdoResponse(
        UUID id,
        UUID bdoId,
        UUID entregaId,
        String motivo,
        String complemento,
        String canceladoPorNome,
        String motoristaAnteriorNome,
        boolean emRota,
        Instant dataHora) {
}
