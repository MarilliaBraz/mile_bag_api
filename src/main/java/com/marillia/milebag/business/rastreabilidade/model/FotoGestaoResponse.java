package com.marillia.milebag.business.rastreabilidade.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma foto da entrega para a empresa (V48). {@code tipo}: RETIRADA, AVARIA_RETIRADA, PORTA, AVARIA_ENTREGA ou
 * BDO_ASSINADO (a foto do documento, guardada no comprovante). O arquivo vem de .../fotos/{id}. V51: {@code substituida}
 * marca a foto trocada por uma correcao - continua visivel para auditoria.
 */
public record FotoGestaoResponse(UUID id, String tipo, Instant dataHora, boolean substituida) {
}
