package com.marillia.milebag.core.exception;

/**
 * Endereco de entrega nao pode ser geocodificado automaticamente e nenhuma
 * coordenada manual foi informada (HU03, FR-006, contracts/rota-api.md -
 * 422 Unprocessable Entity).
 */
public class EnderecoNaoGeocodificavelException extends RuntimeException {

    public EnderecoNaoGeocodificavelException(String mensagem) {
        super(mensagem);
    }
}
