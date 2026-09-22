package com.marillia.milebag.business.viagem.model;

/**
 * {@code A_CAMINHO}/{@code CHEGADA} vem de HU19/HU20 (marcos de progresso,
 * ainda sem HU propria); {@code COMPROVANTE}/{@code OCORRENCIA} vem de
 * HU06/HU07 (RD06/RD07) - nenhuma delas implementada ainda, mas a tabela ja
 * aceita os quatro tipos desde ja (ver data-model.md desta HU).
 */
public enum TipoCheckpoint {
    PARTIDA,
    A_CAMINHO,
    CHEGADA,
    COMPROVANTE,
    OCORRENCIA
}
