package com.marillia.milebag.business.rota.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.rota.model.CalcularRotaRequest;
import com.marillia.milebag.business.rota.model.CalcularRotaResponse;
import com.marillia.milebag.business.rota.model.SelecionarRotaRequest;
import com.marillia.milebag.business.rota.model.SelecionarRotaResponse;
import com.marillia.milebag.business.rota.service.RotaService;

/** Endpoints de calculo e selecao de rota (HU03) - ver contracts/rota-api.md. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/rotas")
public class RotaController {

    private final RotaService rotaService;

    public RotaController(RotaService rotaService) {
        this.rotaService = rotaService;
    }

    @PostMapping("/calcular")
    public CalcularRotaResponse calcular(@PathVariable UUID entregaId, @RequestBody(required = false) CalcularRotaRequest request) {
        return rotaService.calcular(entregaId, request);
    }

    @PostMapping("/selecionar")
    public SelecionarRotaResponse selecionar(@PathVariable UUID entregaId, @RequestBody SelecionarRotaRequest request) {
        return rotaService.selecionar(entregaId, request);
    }
}
