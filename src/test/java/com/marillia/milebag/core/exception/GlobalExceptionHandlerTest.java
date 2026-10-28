package com.marillia.milebag.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    static class Rotas {
        void porCaminho(@PathVariable UUID token) {}

        void porConsulta(@RequestParam UUID tenantId) {}
    }

    /** Link de convite com token que nao e UUID: antes vinha "Failed to convert 'token'..." para a tela. */
    @Test
    void identificadorMalformadoNoCaminhoViraNaoEncontradoEmPortugues() throws Exception {
        ProblemDetail problema = responder(erro("porCaminho", "token"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problema.getType().toString()).endsWith("/recurso-nao-encontrado");
        assertThat(problema.getDetail()).isEqualTo("Endereco incompleto ou incorreto: confira o link.");
    }

    @Test
    void valorInvalidoEmParametroDeConsultaViraCampoInvalido() throws Exception {
        ProblemDetail problema = responder(erro("porConsulta", "tenantId"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getType().toString()).endsWith("/campo-invalido");
        assertThat(problema.getDetail()).isEqualTo("Valor invalido para o campo tenantId.");
    }

    private static MethodArgumentTypeMismatchException erro(String metodo, String nome) throws Exception {
        MethodParameter parametro = new MethodParameter(Rotas.class.getDeclaredMethod(metodo, UUID.class), 0);
        return new MethodArgumentTypeMismatchException("token-invalido", UUID.class, nome, parametro,
                new IllegalArgumentException("Invalid UUID string: token-invalido"));
    }

    private ProblemDetail responder(MethodArgumentTypeMismatchException ex) {
        ResponseEntity<Object> resposta = handler.handleTypeMismatch(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                new ServletWebRequest(new MockHttpServletRequest()));
        assertThat(resposta).isNotNull();
        return (ProblemDetail) resposta.getBody();
    }
}
