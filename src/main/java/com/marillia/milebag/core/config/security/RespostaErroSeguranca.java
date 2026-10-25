package com.marillia.milebag.core.config.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Erros barrados pelo Spring Security, antes de chegar a um controller, no mesmo formato
 * {@code application/problem+json} do {@code GlobalExceptionHandler}.
 *
 * <p>Sem isto, token ausente, invalido ou expirado voltava 403 "Forbidden" (o padrao do Spring sem ponto de
 * entrada), igual a falta de permissao: o app nao tinha como saber que bastava entrar de novo. Agora sessao
 * invalida e 401 {@code sessao-expirada} e falta de permissao e 403 {@code acesso-negado}.</p>
 */
public class RespostaErroSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String TIPO_BASE = "https://milebag.com.br/erros/";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException excecao) throws IOException {
        escrever(response, HttpStatus.UNAUTHORIZED, "sessao-expirada", "Sua sessao expirou ou nao e valida. Entre de novo para continuar.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException excecao) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, "acesso-negado", "Voce nao tem permissao para esta acao.");
    }

    private static void escrever(HttpServletResponse response, HttpStatus status, String tipo, String detalhe) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Textos fixos, sem dado do cliente: nao precisam de escape.
        response.getWriter().write("{\"type\":\"" + TIPO_BASE + tipo + "\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detalhe + "\"}");
    }
}
