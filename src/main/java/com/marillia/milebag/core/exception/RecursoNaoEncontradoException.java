package com.marillia.milebag.core.exception;

/**
 * Recurso inexistente ou pertencente a outro tenant. As duas situacoes
 * resultam no mesmo 404 - nunca revelar a um tenant que um recurso de outro
 * tenant existe (RNF01).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
