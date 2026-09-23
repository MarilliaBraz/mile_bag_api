package com.marillia.milebag.business.entrega.model;

import java.util.List;

/** Resposta de GET /api/v1/motorista/bdos-disponiveis (HU19, FR-007). */
public record BdosDisponiveisResponse(List<ItemBdoDisponivelResponse> bdos) {
}
