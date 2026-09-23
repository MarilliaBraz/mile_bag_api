package com.marillia.milebag.business.viagem.controller;

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

import com.marillia.milebag.business.viagem.model.AjusteViagemResponse;
import com.marillia.milebag.business.viagem.model.RegistrarAjusteRequest;
import com.marillia.milebag.business.viagem.service.AjusteViagemService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Correcoes de valores calculados de uma viagem (HU17) - ver contracts/relatorio-motorista-api.md. */
@RestController
@RequestMapping("/api/v1/motorista/viagens/{viagemId}/ajustes")
public class AjusteViagemController {

    private final AjusteViagemService ajusteViagemService;

    public AjusteViagemController(AjusteViagemService ajusteViagemService) {
        this.ajusteViagemService = ajusteViagemService;
    }

    @GetMapping
    public List<AjusteViagemResponse> listar(@PathVariable UUID viagemId, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ajusteViagemService.listarPorViagem(viagemId, usuario.userId()).stream().map(AjusteViagemResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AjusteViagemResponse registrar(
            @PathVariable UUID viagemId, @RequestBody RegistrarAjusteRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        var ajuste = ajusteViagemService.registrarAjuste(viagemId, usuario.userId(), request.campo(), request.valorNovo(), request.justificativa());
        return AjusteViagemResponse.de(ajuste);
    }
}
