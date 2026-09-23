package com.marillia.milebag.business.entrega.model;

import java.util.UUID;

/** Bagagem carona vinculada, como exibida dentro do painel/detalhe da entrega principal (HU19, FR-003). */
public record BagagemCaronaResumo(UUID id, String etiquetaBagagem) {
}
