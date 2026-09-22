package com.marillia.milebag.core.exception;

/**
 * Tentativa de iniciar a viagem sem uma rota selecionada para a entrega
 * (HU04, FR-001, contracts/viagem-api.md - 422 Unprocessable Entity).
 */
public class RotaNaoSelecionadaException extends RuntimeException {

    public RotaNaoSelecionadaException(String mensagem) {
        super(mensagem);
    }
}
