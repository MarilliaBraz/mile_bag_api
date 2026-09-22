package com.marillia.milebag.business.tenant.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tenant.dto.AtivarConviteRequest;
import com.marillia.milebag.business.tenant.dto.ConviteConsultaResponse;
import com.marillia.milebag.business.tenant.service.ConviteAdministradorService;

/**
 * Ativacao do convite do primeiro administrador do tenant (HU15) - ver
 * contracts/convite-api.md. Publico (SecurityConfig): a tela de ativacao
 * roda antes de qualquer login, entao nao ha JWT a apresentar.
 */
@RestController
@RequestMapping("/api/v1/convites")
public class ConviteController {

    private final ConviteAdministradorService conviteAdministradorService;

    public ConviteController(ConviteAdministradorService conviteAdministradorService) {
        this.conviteAdministradorService = conviteAdministradorService;
    }

    @GetMapping("/{token}")
    public ConviteConsultaResponse consultar(@PathVariable UUID token) {
        return conviteAdministradorService.consultar(token);
    }

    @PostMapping("/{token}/ativar")
    @ResponseStatus(HttpStatus.CREATED)
    public void ativar(@PathVariable UUID token, @RequestBody AtivarConviteRequest request) {
        conviteAdministradorService.ativar(token, request.senha());
    }
}
