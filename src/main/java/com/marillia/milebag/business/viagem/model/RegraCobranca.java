package com.marillia.milebag.business.viagem.model;

/**
 * Como a viagem e paga (V52, item 7): entrega na cidade da base (o aeroporto de onde a mala sai) paga o valor fixo da
 * tarifa; fora dela, km x valor por km, com ida e volta a partir do aeroporto.
 */
public enum RegraCobranca {
    FIXO_CIDADE_BASE,
    POR_KM
}
