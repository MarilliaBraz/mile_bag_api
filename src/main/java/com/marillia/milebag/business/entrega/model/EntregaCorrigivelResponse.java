package com.marillia.milebag.business.entrega.model;

import java.time.Instant;
import java.util.UUID;

/** V51: entrega finalizada pelo motorista que ele ainda pode corrigir (ate {@code corrigivelAte}). */
public record EntregaCorrigivelResponse(UUID entregaId, String bdoNumero, String passageiroNome, Instant corrigivelAte) {
}
