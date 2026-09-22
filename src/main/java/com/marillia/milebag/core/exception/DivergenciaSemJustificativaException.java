package com.marillia.milebag.core.exception;

/**
 * A quilometragem consolidada diverge da rota inicialmente sugerida acima
 * do limiar de RN14 e nenhuma justificativa (ou uma em branco) foi
 * informada (HU04, FR-008, contracts/viagem-api.md - 422 Unprocessable
 * Entity). Nao e um erro fatal: o chamador (HU06/HU07) deve reapresentar a
 * mesma tela pedindo a justificativa e reenviar o encerramento.
 */
public class DivergenciaSemJustificativaException extends RuntimeException {

    public DivergenciaSemJustificativaException(String mensagem) {
        super(mensagem);
    }
}
