package com.marillia.milebag.core.exception;

/** Muitas tentativas de login erradas para o mesmo login (HU22) - bloqueio temporario contra adivinhacao de senha. */
public class LoginBloqueadoException extends RuntimeException {

    public LoginBloqueadoException(long minutosRestantes) {
        super("Muitas tentativas de login. Tente novamente em " + minutosRestantes + " minuto(s).");
    }
}
