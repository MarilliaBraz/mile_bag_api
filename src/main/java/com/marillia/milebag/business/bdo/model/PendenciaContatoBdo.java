package com.marillia.milebag.business.bdo.model;

/**
 * Dado de contato do passageiro que falta ou esta fora do formato no BDO
 * (RN15, HU28) - so sinaliza, nunca bloqueia confirmacao, autoatribuicao ou
 * entrega.
 */
public enum PendenciaContatoBdo {
    ENDERECO_AUSENTE,
    ENDERECO_SEM_NUMERO,
    TELEFONE_AUSENTE,
    TELEFONE_INVALIDO
}
