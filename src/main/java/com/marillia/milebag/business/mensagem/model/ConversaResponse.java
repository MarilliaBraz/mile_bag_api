package com.marillia.milebag.business.mensagem.model;

import java.util.List;

/** Ver contracts/mensagens-api.md desta HU. */
public record ConversaResponse(ContatoDisponivelResponse contatoDisponivel, List<MensagemResponse> mensagens) {
}
