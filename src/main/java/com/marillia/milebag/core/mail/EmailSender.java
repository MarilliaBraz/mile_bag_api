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
    default void enviar(String destinatario, String assunto, String corpo, String replyTo) throws MessagingException {
        enviar(null, destinatario, assunto, corpo, replyTo);
    }

    /** {@code conta}: nula usa a conta padrao de {@code milebag.smtp}. */
    default void enviar(ContaEnvioEmail conta, String destinatario, String assunto, String corpo, String replyTo) throws MessagingException {
        enviar(conta, destinatario, assunto, corpo, replyTo, null);
    }

    /** Com um arquivo anexado pela conta padrao (segunda via do BDO, V50). */
    default void enviarComAnexo(String destinatario, String assunto, String corpo, AnexoEmail anexo) throws MessagingException {
        enviar(null, destinatario, assunto, corpo, null, anexo);
    }

    /** {@code anexo}: nulo envia so o texto, como sempre. */
    void enviar(ContaEnvioEmail conta, String destinatario, String assunto, String corpo, String replyTo, AnexoEmail anexo)
            throws MessagingException;
}
