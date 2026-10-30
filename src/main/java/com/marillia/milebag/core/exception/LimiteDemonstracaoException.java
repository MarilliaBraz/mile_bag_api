package com.marillia.milebag.core.exception;

/** Muitos pedidos de demonstracao do mesmo IP (HU38) - freio contra envio automatico pela pagina publica. */
public class LimiteDemonstracaoException extends RuntimeException {

    public LimiteDemonstracaoException() {
        super("Muitos pedidos. Tente novamente mais tarde ou fale pelo WhatsApp.");
    }
}
