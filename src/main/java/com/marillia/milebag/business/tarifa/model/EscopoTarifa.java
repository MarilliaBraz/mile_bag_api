package com.marillia.milebag.business.tarifa.model;

/** Ordem de especificidade decrescente (FR-004): MOTORISTA > REGIAO > PADRAO. */
public enum EscopoTarifa {
    PADRAO,
    MOTORISTA,
    REGIAO
}
