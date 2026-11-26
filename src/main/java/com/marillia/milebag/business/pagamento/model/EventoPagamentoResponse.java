package com.marillia.milebag.business.pagamento.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventoPagamentoResponse(TipoEventoPagamento tipo, LocalDate dataReferencia, String motivo, UUID usuarioId, Instant dataHora) {

    public static EventoPagamentoResponse de(EventoPagamentoMotorista evento) {
        return new EventoPagamentoResponse(evento.getTipo(), evento.getDataReferencia(), evento.getMotivo(), evento.getUsuarioId(),
                evento.getDataHora());
    }
}
