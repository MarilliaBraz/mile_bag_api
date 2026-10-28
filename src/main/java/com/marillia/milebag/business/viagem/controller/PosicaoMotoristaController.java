package com.marillia.milebag.business.viagem.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.viagem.model.RegistrarPosicaoRequest;
import com.marillia.milebag.business.viagem.model.RegistrarPosicaoResponse;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Posicao atual do motorista para o link "Acompanhe sua bagagem" (HU31) - ver
 * specs/HU31-acompanhar-bagagem/contracts/acompanhamento-api.md. Sem entrega no caminho: a posicao vai para a
 * viagem em andamento do proprio motorista, nunca para a de outro.
 */
@RestController
public class PosicaoMotoristaController {

    private final ViagemService viagemService;

    public PosicaoMotoristaController(ViagemService viagemService) {
        this.viagemService = viagemService;
    }

    @PostMapping("/api/v1/motorista/posicao")
    public RegistrarPosicaoResponse registrar(@RequestBody RegistrarPosicaoRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        if (request == null || request.latitude() == null || request.longitude() == null
                || Math.abs(request.latitude()) > 90 || Math.abs(request.longitude()) > 180) {
            throw new RegraNegocioException("Informe latitude e longitude validas.");
        }
        return new RegistrarPosicaoResponse(viagemService.registrarPosicao(usuario.userId(), request.latitude(), request.longitude()));
    }
}
