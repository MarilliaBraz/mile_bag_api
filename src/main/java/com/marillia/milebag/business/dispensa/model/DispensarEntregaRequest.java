package com.marillia.milebag.business.dispensa.model;

import java.util.UUID;

/** {@code motivoId}: um motivo de dispensa da empresa - obrigatorio se a entrega ja teve alguma viagem. */
public record DispensarEntregaRequest(UUID motivoId, String complemento) {
}
