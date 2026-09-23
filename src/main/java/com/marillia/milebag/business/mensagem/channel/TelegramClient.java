package com.marillia.milebag.business.mensagem.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marillia.milebag.core.config.TelegramProperties;

/**
 * Cliente HTTP para a Bot API do Telegram (HU18, research.md item 1) -
 * texto livre, sem necessidade de templates pre-aprovados (diferente da
 * WhatsApp Business API da decisao original). {@code habilitado} comeca
 * falso ate o bot ser criado via {@code @BotFather} e o token configurado.
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private final RestClient restClient;
    private final TelegramProperties properties;

    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl("https://api.telegram.org").build();
    }

    /** {@code false} tambem quando desabilitado (sem token configurado) - o chamador decide o {@code statusEnvio} resultante (FR-009). */
    public boolean enviar(long chatId, String texto) {
        if (!properties.habilitado()) {
            log.warn("Envio via Telegram desabilitado (milebag.telegram.habilitado=false) - nenhuma mensagem enviada ao chat {}.", chatId);
            return false;
        }
        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", properties.botToken())
                    .body(new SendMessageRequest(chatId, texto))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Falha ao enviar mensagem via Telegram para o chat {}.", chatId, e);
            return false;
        }
    }

    private record SendMessageRequest(@JsonProperty("chat_id") long chatId, String text) {
    }
}
