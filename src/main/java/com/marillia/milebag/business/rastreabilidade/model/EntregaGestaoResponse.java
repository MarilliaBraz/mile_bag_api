package com.marillia.milebag.business.rastreabilidade.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.business.entrega.model.EntregaStatus;

/** Uma linha da tela "Entregas" da empresa (V46). {@code motoristaNome} nulo: entrega sem motorista no momento. */
public record EntregaGestaoResponse(
        UUID entregaId,
        EntregaStatus status,
        String bdoNumero,
        String passageiroNome,
        String endereco,
        String motoristaNome,
        Instant atualizadoEm) {
}
