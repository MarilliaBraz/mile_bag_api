package com.marillia.milebag.business.entrega.model;

/**
 * Ciclo de vida da entrega (RD05) - definido canonicamente aqui (HU02);
 * demais HUs (HU04, HU06, HU07) reaproveitam estes mesmos valores.
 * Ver data-model.md desta HU.
 */
public enum EntregaStatus {
    AGUARDANDO_ATRIBUICAO,
    ATRIBUIDA,
    EM_ANDAMENTO,
    ENCERRADA_COM_SUCESSO,
    REABERTA,
    /** V47: a empresa cancelou o BDO. Definitiva - sem motorista, fora dos disponiveis e do painel, nao e paga. */
    CANCELADA
}
