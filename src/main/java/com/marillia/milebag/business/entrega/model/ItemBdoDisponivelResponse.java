package com.marillia.milebag.business.entrega.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.business.bdo.model.BdoOrigem;

/** Um BDO confirmado ainda sem motorista responsavel (HU19, FR-007) - ver contracts/painel-api.md. */
public record ItemBdoDisponivelResponse(
        UUID bdoId,
        BdoOrigem origem,
        String bdoNumero,
        String passageiroNome,
        String endereco,
        String etiquetaBagagem,
        Instant dataCaptura) {
}
