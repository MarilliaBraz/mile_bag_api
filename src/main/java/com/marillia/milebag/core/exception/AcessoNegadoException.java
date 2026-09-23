package com.marillia.milebag.core.exception;

/** O recurso existe (e pertence ao tenant do usuario autenticado), mas nao a ele especificamente - ex: entrega de outro motorista. */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
