package com.marillia.milebag.business.mensagem.model;

import java.time.Instant;
import java.util.UUID;

/** Ver contracts/mensagens-api.md desta HU. */
public record MensagemResponse(
        UUID id,
        AutorMensagem autor,
        CanalMensagem canal,
        TipoMensagem tipo,
        String conteudo,
        StatusEnvioMensagem statusEnvio,
        Instant criadoEm,
        TipoEventoNotificacao tipoEvento) {

    public static MensagemResponse de(Mensagem mensagem) {
        return new MensagemResponse(
                mensagem.getId(), mensagem.getAutor(), mensagem.getCanal(), mensagem.getTipo(), mensagem.getConteudo(),
                mensagem.getStatusEnvio(), mensagem.getCriadoEm(), mensagem.getTipoEvento());
    }
}
