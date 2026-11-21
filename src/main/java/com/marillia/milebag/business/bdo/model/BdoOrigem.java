package com.marillia.milebag.business.bdo.model;

/** Como o BDO entrou no sistema (ver data-model.md desta HU e de HU02, RF21). */
public enum BdoOrigem {
    /** Captura por foto do motorista - desativada (V49); so nos BDOs antigos. */
    FOTO_MANUAL,
    EMAIL_AUTOMATICO,
    /** Encaminhado por uma companhia aerea parceira (HU37, RF30). */
    COMPANHIA_AEREA,
    /** Incluido pela empresa no painel, colando o telex ou digitando os campos (V49). */
    EMPRESA
}
