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

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.encaminhamento.model.EncaminharBagagemRequest;
import com.marillia.milebag.business.encaminhamento.service.EncaminhamentoService;
import com.marillia.milebag.business.encaminhamento.service.ParceriaEntregaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Encaminhamento para entrega pela companhia (HU37) - ver specs/HU37-encaminhar-entrega/contracts/encaminhamento-api.md. */
@RestController
@RequestMapping("/api/v1/companhia")
public class EncaminhamentoController {

    private final EncaminhamentoService service;
    private final ParceriaEntregaService parceriaService;

    public EncaminhamentoController(EncaminhamentoService service, ParceriaEntregaService parceriaService) {
        this.service = service;
        this.parceriaService = parceriaService;
    }

    @GetMapping("/empresas-entrega")
    public List<ParceriaEntregaService.EmpresaEntrega> parceiras(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return parceriaService.parceirasDa(usuario.tenantId());
    }

    @PostMapping("/bagagens/{id}/encaminhamento")
    @ResponseStatus(HttpStatus.CREATED)
    public BagagemEncontradaResponse encaminhar(
            @PathVariable UUID id, @RequestBody EncaminharBagagemRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.encaminhar(id, request, usuario.userId());
    }

    @DeleteMapping("/bagagens/{id}/encaminhamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        service.cancelar(id, usuario.userId());
    }
}
