package com.marillia.milebag.core.exception;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda excecao em {@code application/problem+json} (RFC 7807), com
 * um {@code type} estavel para o front-end decidir a acao e um {@code
 * detail} sem stacktrace nem texto tecnico cru - contrato de erro exigido em
 * {@code ihc-diretrizes.md} secao 4 ("Recuperacao").
 *
 * <p>Nunca deixar uma excecao nao mapeada vazar como 500 com corpo generico
 * do servidor: o handler de {@link Exception} no fim deste arquivo garante
 * que toda resposta de erro segue o mesmo formato, mesmo para falhas
 * inesperadas - sem revelar detalhe interno ao cliente (mensagem generica),
 * mas com o stacktrace completo no log do servidor para diagnostico.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TIPO_BASE = "https://milebag.com.br/erros/";

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "recurso-nao-encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail handleConflito(ConflitoException ex) {
        return problema(HttpStatus.CONFLICT, "conflito", ex.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail handleRegraNegocio(RegraNegocioException ex) {
        return problema(HttpStatus.BAD_REQUEST, "regra-de-negocio", ex.getMessage());
    }

    @ExceptionHandler(EnderecoNaoGeocodificavelException.class)
    public ProblemDetail handleEnderecoNaoGeocodificavel(EnderecoNaoGeocodificavelException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "endereco-nao-geocodificavel", ex.getMessage());
    }

    @ExceptionHandler(RotaNaoSelecionadaException.class)
    public ProblemDetail handleRotaNaoSelecionada(RotaNaoSelecionadaException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "rota-nao-selecionada", ex.getMessage());
    }

    @ExceptionHandler(DivergenciaSemJustificativaException.class)
    public ProblemDetail handleDivergenciaSemJustificativa(DivergenciaSemJustificativaException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "divergencia-trajeto-sem-justificativa", ex.getMessage());
    }

    @ExceptionHandler(ComprovanteIncompletoException.class)
    public ProblemDetail handleComprovanteIncompleto(ComprovanteIncompletoException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "comprovante-incompleto", ex.getMessage());
    }

    @ExceptionHandler(ArquivoInvalidoException.class)
    public ProblemDetail handleArquivoInvalido(ArquivoInvalidoException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "arquivo-invalido", ex.getMessage());
    }

    @ExceptionHandler(ConviteExpiradoException.class)
    public ProblemDetail handleConviteExpirado(ConviteExpiradoException ex) {
        return problema(HttpStatus.GONE, "convite-expirado", ex.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ProblemDetail handleAcessoNegado(AcessoNegadoException ex) {
        return problema(HttpStatus.FORBIDDEN, "acesso-negado", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detalhe = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Um ou mais campos obrigatorios estao ausentes ou invalidos.");
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "campo-invalido", detalhe);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problema);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenerico(Exception ex) {
        log.error("Erro nao tratado", ex);
        return problema(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "erro-interno",
                "Ocorreu um erro inesperado. Tente novamente em instantes.");
    }

    private ProblemDetail problema(HttpStatus status, String tipo, String detalhe) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detalhe);
        problemDetail.setType(URI.create(TIPO_BASE + tipo));
        return problemDetail;
    }
}
