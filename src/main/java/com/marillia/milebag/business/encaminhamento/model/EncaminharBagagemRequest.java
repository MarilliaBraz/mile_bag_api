package com.marillia.milebag.business.encaminhamento.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Ver specs/HU37-encaminhar-entrega/contracts/encaminhamento-api.md. V64: {@code processoAbertoEm} (abertura do PIR;
 * vazio = registro da bagagem) e {@code tipoVoo} (vazio = DOMESTICO), para o prazo da ANAC.
 */
public record EncaminharBagagemRequest(
        UUID empresaId, String numeroProcesso, String passageiroNome, String endereco, String telefone, String email,
        Instant processoAbertoEm, String tipoVoo) {

    public EncaminharBagagemRequest(UUID empresaId, String numeroProcesso, String passageiroNome, String endereco, String telefone,
            String email) {
        this(empresaId, numeroProcesso, passageiroNome, endereco, telefone, email, null, null);
    }
}
