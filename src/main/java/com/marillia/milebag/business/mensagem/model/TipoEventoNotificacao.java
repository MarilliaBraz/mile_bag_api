package com.marillia.milebag.business.mensagem.model;

/**
 * As cinco transicoes de estado da entrega que disparam notificacao
 * automatica (FR-005, research.md item 5) - distinto de {@code
 * TipoCheckpoint} (business/viagem): nem todo {@code TipoCheckpoint} vira
 * evento de notificacao (ex.: {@code PARTIDA}), e dois destes eventos
 * (viagem iniciada, comprovante/ocorrencia) nao sao checkpoints.
 */
public enum TipoEventoNotificacao {
    VIAGEM_INICIADA,
    A_CAMINHO,
    CHEGADA,
    COMPROVANTE_REGISTRADO,
    OCORRENCIA_REGISTRADA,
    /** HU29: a nova tentativa ja tem data e horario combinados - o texto leva a janela. */
    REAGENDAMENTO_REGISTRADO
}
