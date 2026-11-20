package com.marillia.milebag.business.rastreabilidade.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.rastreabilidade.model.EntregaGestaoResponse;
import com.marillia.milebag.business.rastreabilidade.model.HistoricoEntregaResponse;
import com.marillia.milebag.business.rastreabilidade.service.RastreabilidadeEntregaService;

/** Tela "Entregas" do back-office da empresa (V46) - so o administrador do tenant (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/gestao/entregas")
public class GestaoEntregasController {

    private final RastreabilidadeEntregaService rastreabilidadeEntregaService;

    public GestaoEntregasController(RastreabilidadeEntregaService rastreabilidadeEntregaService) {
        this.rastreabilidadeEntregaService = rastreabilidadeEntregaService;
    }

    @GetMapping
    public List<EntregaGestaoResponse> listar(@RequestParam(required = false) EntregaStatus status) {
        return rastreabilidadeEntregaService.listar(status);
    }

    @GetMapping("/{entregaId}/historico")
    public HistoricoEntregaResponse historico(@PathVariable UUID entregaId) {
        return rastreabilidadeEntregaService.historico(entregaId);
    }
}
