package com.marillia.milebag.business.entrega.model;

import java.util.List;

/** Resposta de GET /api/v1/motorista/painel-entregas (HU19, FR-002). */
public record PainelEntregasResponse(int contadorPendentes, List<ItemPainelResponse> entregas) {
}
