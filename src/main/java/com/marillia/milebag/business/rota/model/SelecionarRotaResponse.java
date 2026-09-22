package com.marillia.milebag.business.rota.model;

import java.util.UUID;

/** Ver contracts/rota-api.md. */
public record SelecionarRotaResponse(
        UUID entregaId, TipoSelecao tipoSelecao, RotaAlternativa rotaEscolhida, String justificativa) {
}
