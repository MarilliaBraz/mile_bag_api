package com.marillia.milebag.business.configuracao.model;

import java.util.UUID;

/** Base aeroportuaria da empresa (V52): com a marca de principal e a cidade do aeroporto (a "cidade da base"). */
public record BaseAeroportuariaResponse(UUID id, String codigoIata, String nome, boolean principal, String cidade) {
}
