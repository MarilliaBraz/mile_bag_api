package com.marillia.milebag.business.entrega.model;

import java.util.UUID;

/** Representacao de uma Entrega exposta pela API - ver contracts/entrega-api.md. */
public record EntregaResponse(
        UUID id,
        UUID bdoId,
        EntregaStatus status,
        UUID motoristaResponsavel,
        EntregaBdoResumo bdo) {
}
