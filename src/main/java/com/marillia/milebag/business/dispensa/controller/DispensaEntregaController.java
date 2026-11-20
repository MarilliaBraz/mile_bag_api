package com.marillia.milebag.business.dispensa.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.dispensa.model.DispensaEntregaResponse;
import com.marillia.milebag.business.dispensa.model.DispensarEntregaRequest;
import com.marillia.milebag.business.dispensa.service.DispensaEntregaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Dispensa da entrega pelo motorista e o historico de dispensas (V46). Rota ja liberada a motorista e empresa. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/dispensas")
public class DispensaEntregaController {

    private final DispensaEntregaService dispensaEntregaService;

    public DispensaEntregaController(DispensaEntregaService dispensaEntregaService) {
        this.dispensaEntregaService = dispensaEntregaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DispensaEntregaResponse dispensar(
            @PathVariable UUID entregaId,
            @RequestBody(required = false) DispensarEntregaRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return dispensaEntregaService.dispensar(entregaId, usuario.userId(), request);
    }

    @GetMapping
    public List<DispensaEntregaResponse> listar(@PathVariable UUID entregaId, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return dispensaEntregaService.listarParaUsuario(entregaId, usuario.userId(), usuario.papel());
    }
}
