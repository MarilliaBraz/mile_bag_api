package com.marillia.milebag.core.exception;

/**
 * Falta um dos campos obrigatorios do comprovante de entrega (HU06, FR-002,
 * contracts/comprovante-entrega-api.md - 422 Unprocessable Entity).
 */
public class ComprovanteIncompletoException extends RuntimeException {

    public ComprovanteIncompletoException(String mensagem) {
        super(mensagem);
    }
}
