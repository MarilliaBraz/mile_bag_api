package com.marillia.milebag.business.mensagem.channel;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marillia.milebag.core.config.MensageriaProperties;
import com.marillia.milebag.core.config.TelegramProperties;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

/**
 * Envio por e-mail da conversa com o passageiro (HU18) - {@code Reply-To}
 * unico por entrega (data-model.md, "Enderecamento por canal") e, enquanto
 * o Telegram ainda nao estiver vinculado aquela entrega, o link de convite
 * (FR-003a). Reaproveita {@link EmailSender} (mesmo SMTP self-hosted de
 * HU15), nao um novo cliente.
 */
@Component
public class EmailChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final EmailSender emailSender;
    private final MensageriaProperties mensageriaProperties;
    private final TelegramProperties telegramProperties;

    public EmailChannel(EmailSender emailSender, MensageriaProperties mensageriaProperties, TelegramProperties telegramProperties) {
        this.emailSender = emailSender;
        this.mensageriaProperties = mensageriaProperties;
        this.telegramProperties = telegramProperties;
    }

    /** {@code false} em qualquer falha de envio - o chamador decide o {@code statusEnvio} resultante (FR-009), nunca lanca. */
    public boolean enviar(UUID entregaId, String destinatario, String assunto, String conteudo, boolean incluirConviteTelegram) {
        String corpo = incluirConviteTelegram ? conteudo + "\n\n" + linkConvite(entregaId) : conteudo;
        try {
            emailSender.enviar(destinatario, assunto, corpo, enderecoResposta(entregaId));
            return true;
        } catch (MessagingException e) {
            log.warn("Falha ao enviar e-mail da conversa da entrega {} para {}.", entregaId, destinatario, e);
            return false;
        }
    }

    /** {@code Reply-To} usado tanto no envio quanto para resolver o {@code entregaId} de uma resposta recebida ({@code EmailInboundListener}). */
    public String enderecoResposta(UUID entregaId) {
        return "entrega-" + entregaId + "@" + mensageriaProperties.dominioEmail();
    }

    private String linkConvite(UUID entregaId) {
        return "Para conversar em tempo real pelo Telegram, toque no link: https://t.me/" + telegramProperties.botUsername() + "?start="
                + entregaId;
    }
}
