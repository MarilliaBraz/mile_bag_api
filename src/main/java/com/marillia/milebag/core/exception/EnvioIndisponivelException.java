package com.marillia.milebag.core.exception;

/** O e-mail nao pode ser enviado agora (SMTP desligado ou falhando) - o pedido de demonstracao (HU38) nao se perde em silencio. */
public class EnvioIndisponivelException extends RuntimeException {

    public EnvioIndisponivelException() {
        super("Nao foi possivel enviar o pedido agora. Fale conosco pelo WhatsApp.");
    }
}
