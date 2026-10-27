package com.marillia.milebag.business.bdo.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representacao de um BDO exposta pela API - ver contracts/bdo-api.md. */
public record BdoResponse(
        UUID id,
        BdoOrigem origem,
        CamposExtraidosBdo camposExtraidos,
        String imagemOriginal,
        StatusConferencia statusConferencia,
        Instant dataCaptura,
        List<PendenciaContatoBdo> pendenciasContato) {
}
