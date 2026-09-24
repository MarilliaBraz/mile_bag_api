package com.marillia.milebag.core.exception;

/** Login ou senha incorretos (HU22) - mesma mensagem para "usuario nao existe" e "senha errada", para nao revelar quais logins existem. */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Login ou senha invalidos.");
    }
}
