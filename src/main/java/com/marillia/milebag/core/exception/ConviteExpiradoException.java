package com.marillia.milebag.core.exception;

/** Convite de administrador (HU15) com prazo de validade vencido (410 Gone). */
public class ConviteExpiradoException extends RuntimeException {

    public ConviteExpiradoException(String mensagem) {
        super(mensagem);
    }
}
