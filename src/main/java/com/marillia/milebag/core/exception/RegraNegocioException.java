package com.marillia.milebag.core.exception;

/** Violacao de uma regra de negocio explicita do dominio (ex: campo obrigatorio ausente na confirmacao do BDO). */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
