package com.marillia.milebag.business.configuracao.model;

/** {@code reagendamento} nulo = motivo comum de nao entrega (HU29). */
public record CriarMotivoOcorrenciaRequest(String descricao, Boolean reagendamento) {
}
