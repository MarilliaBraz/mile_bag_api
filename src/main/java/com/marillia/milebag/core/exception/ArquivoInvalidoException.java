package com.marillia.milebag.core.exception;

/**
 * O arquivo enviado nao e uma imagem decodificavel - lancado por
 * {@code ArmazenamentoImagemService} antes de gravar qualquer coisa em
 * disco. Nunca confia no {@code Content-Type} ou na extensao do nome de
 * arquivo informados pelo cliente, ambos facilmente forjaveis; o formato
 * real e sempre detectado a partir do conteudo (422 Unprocessable Entity).
 */
public class ArquivoInvalidoException extends RuntimeException {

    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
