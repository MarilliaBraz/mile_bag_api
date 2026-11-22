package com.marillia.milebag.business.basebdo.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.basebdo.model.TrocarBaseBdoRequest;
import com.marillia.milebag.business.basebdo.service.TrocaBaseBdoService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Troca da base de um BDO pela empresa (V52) - so o administrador ({@code /api/v1/gestao/**}). */
@RestController
public class TrocaBaseBdoController {

    private final TrocaBaseBdoService trocaBaseBdoService;

    public TrocaBaseBdoController(TrocaBaseBdoService trocaBaseBdoService) {
        this.trocaBaseBdoService = trocaBaseBdoService;
    }

    @PostMapping("/api/v1/gestao/bdos/{bdoId}/base")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocar(@PathVariable UUID bdoId, @RequestBody TrocarBaseBdoRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        trocaBaseBdoService.trocar(bdoId, request.baseId(), request.motivo(), usuario.userId());
    }
}
