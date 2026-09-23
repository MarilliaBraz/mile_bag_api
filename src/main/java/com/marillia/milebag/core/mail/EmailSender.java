package com.marillia.milebag.core.mail;

import jakarta.mail.MessagingException;

/**
 * Envio de e-mail transacional, reutilizavel por qualquer dominio (convite
 * de administrador - HU15; mensageria com o passageiro - HU18). Fica em
 * {@code core} pelo mesmo motivo de {@code ArmazenamentoImagemService}
 * (CONVENTIONS.md secao 2.2).
 */
public interface EmailSender {

    default void enviar(String destinatario, String assunto, String corpo) throws MessagingException {
        enviar(destinatario, assunto, corpo, null);
    }

    /** {@code replyTo}: nulo mantem o remetente padrao como endereco de resposta (comportamento do HU15). */
    void enviar(String destinatario, String assunto, String corpo, String replyTo) throws MessagingException;
}
