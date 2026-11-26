package com.marillia.milebag.business.painelgestao.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.painelgestao.model.ConfigPainelGestao;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoResponse;
import com.marillia.milebag.business.painelgestao.service.PainelGestaoService;

/** Painel de gestao (fase 1) e a sua configuracao - so o administrador da empresa ({@code /gestao} e {@code /configuracoes}). */
@RestController
public class PainelGestaoController {

    private final PainelGestaoService service;

    public PainelGestaoController(PainelGestaoService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/gestao/painel")
    public PainelGestaoResponse painel() {
        return service.painel();
    }

    @GetMapping("/api/v1/configuracoes/painel-gestao")
    public ConfigPainelGestao configuracao() {
        return service.configuracao();
    }

    @PutMapping("/api/v1/configuracoes/painel-gestao")
    public ConfigPainelGestao salvar(@RequestBody ConfigPainelGestao pedido) {
        return service.salvar(pedido);
    }
}
