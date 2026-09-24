package com.marillia.milebag.business.tenant.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tenant.service.AutenticacaoService;
import com.marillia.milebag.business.tenant.service.RecuperacaoSenhaService;
import com.marillia.milebag.business.tenant.service.TrocaSenhaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/**
 * Login por usuario e senha (HU22) - ver contracts/autenticacao-api.md.
 * Publico (SecurityConfig): e o que emite o JWT, entao nao ha JWT a apresentar.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacaoController {

    public record LoginRequest(String login, String senha) {
    }

    public record LoginResponse(String token, String papel) {
    }

    public record EsqueciSenhaRequest(String login) {
    }

    public record RedefinirSenhaRequest(String token, String senha) {
    }

    public record TrocarSenhaRequest(String senhaAtual, String novaSenha) {
    }

    public record TokenResponse(String token) {
    }

    private final AutenticacaoService autenticacaoService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final TrocaSenhaService trocaSenhaService;

    public AutenticacaoController(
            AutenticacaoService autenticacaoService, RecuperacaoSenhaService recuperacaoSenhaService, TrocaSenhaService trocaSenhaService) {
        this.autenticacaoService = autenticacaoService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.trocaSenhaService = trocaSenhaService;
    }

    @PostMapping("/login")
    public LoginResponse entrar(@RequestBody LoginRequest request) {
        var sessao = autenticacaoService.autenticar(request.login(), request.senha());
        return new LoginResponse(sessao.token(), sessao.papel());
    }

    /** Sempre 204, exista ou nao a conta (HU25) - a resposta nao revela quais logins existem. */
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esqueciSenha(@RequestBody EsqueciSenhaRequest request) {
        recuperacaoSenhaService.solicitar(request.login());
    }

    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@RequestBody RedefinirSenhaRequest request) {
        recuperacaoSenhaService.redefinir(request.token(), request.senha());
    }

    /** Usuario logado troca a propria senha; devolve o token novo (o anterior deixa de valer para contas de empresa). */
    @PostMapping("/senha")
    public TokenResponse trocarSenha(@AuthenticationPrincipal AuthenticatedUser usuario, @RequestBody TrocarSenhaRequest request) {
        return new TokenResponse(trocaSenhaService.trocar(usuario, request.senhaAtual(), request.novaSenha()));
    }
}
