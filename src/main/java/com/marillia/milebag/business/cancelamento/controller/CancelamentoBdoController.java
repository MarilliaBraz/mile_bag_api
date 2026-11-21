package com.marillia.milebag.business.cancelamento.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.cancelamento.model.CancelamentoBdoResponse;
import com.marillia.milebag.business.cancelamento.model.CancelarBdoRequest;
import com.marillia.milebag.business.cancelamento.service.CancelamentoBdoService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Cancelamento do BDO pela empresa (V47) - em /gestao, so o administrador do tenant (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/gestao/bdos/{bdoId}/cancelamento")
public class CancelamentoBdoController {

    private final CancelamentoBdoService cancelamentoBdoService;

    public CancelamentoBdoController(CancelamentoBdoService cancelamentoBdoService) {
        this.cancelamentoBdoService = cancelamentoBdoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CancelamentoBdoResponse cancelar(
            @PathVariable UUID bdoId,
            @RequestBody(required = false) CancelarBdoRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return cancelamentoBdoService.cancelar(bdoId, usuario.userId(), request);
    }
}
