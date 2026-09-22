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
    REABERTA
}
