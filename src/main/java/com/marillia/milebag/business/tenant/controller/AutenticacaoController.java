package com.marillia.milebag.business.tenant.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tenant.service.AutenticacaoService;

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

    private final AutenticacaoService autenticacaoService;

    public AutenticacaoController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    @PostMapping("/login")
    public LoginResponse entrar(@RequestBody LoginRequest request) {
        var sessao = autenticacaoService.autenticar(request.login(), request.senha());
        return new LoginResponse(sessao.token(), sessao.papel());
    }
}
