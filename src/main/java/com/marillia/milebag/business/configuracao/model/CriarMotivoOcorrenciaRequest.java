package com.marillia.milebag.business.configuracao.model;

/** {@code reagendamento} nulo = motivo comum de nao entrega (HU29); {@code finalidade} nula = ocorrencia. */
public record CriarMotivoOcorrenciaRequest(String descricao, Boolean reagendamento, FinalidadeMotivo finalidade) {
}
