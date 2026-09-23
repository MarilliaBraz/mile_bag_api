package com.marillia.milebag.business.rota.model;

import java.util.UUID;

import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.TipoSelecao;

/** Ver contracts/rota-api.md. */
public record SelecionarRotaResponse(
        UUID entregaId, TipoSelecao tipoSelecao, RotaAlternativa rotaEscolhida, String justificativa) {
}
