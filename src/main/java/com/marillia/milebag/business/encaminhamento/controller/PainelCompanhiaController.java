package com.marillia.milebag.business.encaminhamento.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaGestaoResponse;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaResponse;
import com.marillia.milebag.business.encaminhamento.service.PainelCompanhiaGestaoService;
import com.marillia.milebag.business.encaminhamento.service.PainelCompanhiaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Painel de atividades da companhia aerea (administrador e funcionario, {@code /api/v1/companhia/**}). */
@RestController
public class PainelCompanhiaController {

    private final PainelCompanhiaService service;
    private final PainelCompanhiaGestaoService gestaoService;

    public PainelCompanhiaController(PainelCompanhiaService service, PainelCompanhiaGestaoService gestaoService) {
        this.service = service;
        this.gestaoService = gestaoService;
    }

    @GetMapping("/api/v1/companhia/painel")
    public PainelCompanhiaResponse painel(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.painel(usuario);
    }

    /** Painel de gestao (fase 1): relogio da ANAC, tempos, custodia e aeroportos da companhia do token. */
    @GetMapping("/api/v1/companhia/painel/gestao")
    public PainelCompanhiaGestaoResponse gestao(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return gestaoService.painel(usuario.tenantId());
    }
}
