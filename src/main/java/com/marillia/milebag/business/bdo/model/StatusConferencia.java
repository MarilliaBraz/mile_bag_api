package com.marillia.milebag.business.bdo.model;

/**
 * Para {@code origem = FOTO_MANUAL}, so transita para CONFIRMADO por acao
 * explicita do motorista na tela de conferencia (RN01, FR-005). BDOs com
 * {@code origem = EMAIL_AUTOMATICO} nascem diretamente como CONFIRMADO
 * (ver HU02, RF21).
 */
public enum StatusConferencia {
    PENDENTE,
    CONFIRMADO
}
