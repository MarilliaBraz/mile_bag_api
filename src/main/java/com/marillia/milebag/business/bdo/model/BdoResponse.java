package com.marillia.milebag.business.bdo.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** BDO devolvido pela API: na inclusao pela empresa e na conferencia de numero repetido (V49). */
public record BdoResponse(
        UUID id,
        BdoOrigem origem,
        CamposExtraidosBdo campos,
        Instant dataCaptura,
        List<PendenciaContatoBdo> pendenciasContato) {
}
