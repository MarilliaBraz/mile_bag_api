package com.marillia.milebag.business.encaminhamento.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.encaminhamento.model.CustoProcessoRequest;
import com.marillia.milebag.business.encaminhamento.model.CustoProcessoResponse;
import com.marillia.milebag.business.encaminhamento.service.CustoProcessoService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Custos de um processo de bagagem (fase 3 do painel da companhia; {@code /api/v1/companhia/**}). */
@RestController
@RequestMapping("/api/v1/companhia/bagagens/{bagagemId}/custos")
public class CustoProcessoController {

    private final CustoProcessoService service;

    public CustoProcessoController(CustoProcessoService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustoProcessoResponse> listar(@PathVariable UUID bagagemId) {
        return service.listar(bagagemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustoProcessoResponse lancar(@PathVariable UUID bagagemId, @RequestBody CustoProcessoRequest pedido,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.lancar(bagagemId, pedido, usuario.userId());
    }

    @DeleteMapping("/{custoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID bagagemId, @PathVariable UUID custoId) {
        service.remover(bagagemId, custoId);
    }
}
