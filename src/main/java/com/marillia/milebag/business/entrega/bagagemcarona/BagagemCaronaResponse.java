package com.marillia.milebag.business.entrega.bagagemcarona;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Ver contracts/bagagem-carona-api.md. */
public record BagagemCaronaResponse(
        UUID id, UUID entregaPrincipalId, UUID bdoId, BigDecimal quilometragemComplementar, Instant criadoEm) {

    public static BagagemCaronaResponse de(BagagemCarona bagagemCarona) {
        return new BagagemCaronaResponse(
                bagagemCarona.getId(),
                bagagemCarona.getEntregaPrincipalId(),
                bagagemCarona.getBdoId(),
                bagagemCarona.getQuilometragemComplementar(),
                bagagemCarona.getCriadoEm());
    }
}
