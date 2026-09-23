package com.marillia.milebag.business.mensagem.model;

/** Subconjunto minimo do objeto {@code Update} da Bot API do Telegram usado por esta HU (contracts/mensagens-api.md). */
public record TelegramUpdate(TelegramMessage message) {

    public record TelegramMessage(TelegramChat chat, String text) {
    }

    public record TelegramChat(long id) {
    }
}
