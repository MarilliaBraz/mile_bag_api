package com.marillia.milebag.business.encaminhamento.model;

import java.util.UUID;

/** Ver specs/HU37-encaminhar-entrega/contracts/encaminhamento-api.md. */
public record EncaminharBagagemRequest(
        UUID empresaId, String numeroProcesso, String passageiroNome, String endereco, String telefone, String email) {
}
