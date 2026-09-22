package com.marillia.milebag.core.exception;

/** Operacao em conflito com o estado atual do recurso (ex: confirmar um BDO ja confirmado). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
