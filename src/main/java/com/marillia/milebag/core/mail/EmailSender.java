package com.marillia.milebag.core.mail;

import jakarta.mail.MessagingException;

/**
 * Envio de e-mail transacional, reutilizavel por qualquer dominio (hoje so
 * o convite de administrador - HU15). Fica em {@code core} pelo mesmo
 * motivo de {@code ArmazenamentoImagemService} (CONVENTIONS.md secao 2.2).
 */
public interface EmailSender {

    void enviar(String destinatario, String assunto, String corpo) throws MessagingException;
}
