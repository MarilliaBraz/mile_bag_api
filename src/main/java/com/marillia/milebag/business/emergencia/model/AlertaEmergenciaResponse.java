package com.marillia.milebag.business.emergencia.model;

import java.time.Instant;
import java.util.UUID;

/** Ver contracts/emergencia-api.md (HU30). {@code novo}: falso quando o motorista ja tinha um alerta aberto. */
public record AlertaEmergenciaResponse(
        UUID id,
        UUID motoristaId,
        String motoristaNome,
        UUID entregaId,
        Double latitude,
        Double longitude,
        Instant dataHora,
        StatusAlertaEmergencia status,
        String observacao,
        Instant encerradoEm,
        boolean novo) {
}
