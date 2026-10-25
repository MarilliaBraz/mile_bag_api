package com.marillia.milebag.core.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class RespostaErroSegurancaTest {

    private final RespostaErroSeguranca resposta = new RespostaErroSeguranca();

    @Test
    void sessaoAusenteOuExpiradaResponde401SessaoExpirada() throws Exception {
        MockHttpServletResponse saida = new MockHttpServletResponse();

        resposta.commence(new MockHttpServletRequest(), saida, new InsufficientAuthenticationException("sem token"));

        assertThat(saida.getStatus()).isEqualTo(401);
        assertThat(saida.getContentType()).startsWith("application/problem+json");
        assertThat(saida.getContentAsString()).contains("\"type\":\"https://milebag.com.br/erros/sessao-expirada\"", "\"status\":401");
    }

    @Test
    void faltaDePermissaoResponde403AcessoNegado() throws Exception {
        MockHttpServletResponse saida = new MockHttpServletResponse();

        resposta.handle(new MockHttpServletRequest(), saida, new AccessDeniedException("papel errado"));

        assertThat(saida.getStatus()).isEqualTo(403);
        assertThat(saida.getContentAsString()).contains("\"type\":\"https://milebag.com.br/erros/acesso-negado\"", "\"status\":403");
    }
}
