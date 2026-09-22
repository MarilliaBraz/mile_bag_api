package com.marillia.milebag.business.entrega.model;

import com.marillia.milebag.business.bdo.model.BdoOrigem;

/** Dados resumidos do BDO associado, exibidos junto da Entrega (contracts/entrega-api.md). */
public record EntregaBdoResumo(
        BdoOrigem origem,
        String numero,
        String passageiroNome,
        String enderecoEntrega,
        String etiquetaBagagem) {
}
