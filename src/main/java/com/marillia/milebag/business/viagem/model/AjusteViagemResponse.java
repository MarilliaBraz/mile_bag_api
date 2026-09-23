package com.marillia.milebag.business.viagem.model;

import java.time.Instant;
import java.util.UUID;

/** Representacao publica de um {@link AjusteViagem} (HU17, contracts/relatorio-motorista-api.md). */
public record AjusteViagemResponse(
        UUID id, CampoAjusteViagem campo, String valorAnterior, String valorNovo, String justificativa, Instant criadoEm) {

    public static AjusteViagemResponse de(AjusteViagem ajuste) {
        return new AjusteViagemResponse(
                ajuste.getId(), ajuste.getCampo(), ajuste.getValorAnterior(), ajuste.getValorNovo(), ajuste.getJustificativa(),
                ajuste.getCriadoEm());
    }
}
