package com.marillia.milebag.business.entrega.model;

import java.util.List;

/** Resposta de GET /api/v1/motorista/bdos-disponiveis (HU19, FR-007). */
// V52: semBase - o motorista ainda nao tem base, por isso nao ve BDOs (a empresa precisa definir).
public record BdosDisponiveisResponse(List<ItemBdoDisponivelResponse> bdos, boolean semBase) {
}
