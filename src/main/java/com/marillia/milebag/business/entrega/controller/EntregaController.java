package com.marillia.milebag.business.entrega.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.entrega.model.EntregaResponse;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Endpoints de autoatribuicao e listagem de entregas (HU02) - ver contracts/entrega-api.md. */
@RestController
@RequestMapping("/api/v1/entregas")
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    @PostMapping("/{bdoId}/autoatribuir")
    @ResponseStatus(HttpStatus.CREATED)
    public EntregaResponse autoatribuir(@PathVariable UUID bdoId, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return entregaService.autoatribuir(bdoId, usuario.userId());
    }

    @GetMapping(params = "motorista=me")
    public List<EntregaResponse> listarMinhas(
            @RequestParam EntregaStatus status, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return entregaService.listarPorMotorista(usuario.userId(), status);
    }
}
