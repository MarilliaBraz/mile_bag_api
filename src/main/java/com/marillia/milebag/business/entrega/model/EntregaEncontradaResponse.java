package com.marillia.milebag.business.entrega.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Busca do motorista pelo numero do BDO: uma entrega dele, de qualquer epoca; {@code pagoEm} nulo enquanto nao paga. */
public record EntregaEncontradaResponse(
        UUID entregaId, String bdoNumero, EntregaStatus status, String passageiroNome, Instant atualizadoEm, LocalDate pagoEm) {
}
