package com.marillia.milebag.business.cancelamento.model;

import java.util.UUID;

/** {@code motivoId}: um motivo de cancelamento da empresa - sempre obrigatorio. {@code complemento}: texto livre opcional. */
public record CancelarBdoRequest(UUID motivoId, String complemento) {
}
