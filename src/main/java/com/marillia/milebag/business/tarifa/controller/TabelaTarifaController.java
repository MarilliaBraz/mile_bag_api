package com.marillia.milebag.business.tarifa.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tarifa.model.AtualizarTarifaRequest;
import com.marillia.milebag.business.tarifa.model.CriarTarifaRequest;
import com.marillia.milebag.business.tarifa.model.EscopoTarifa;
import com.marillia.milebag.business.tarifa.model.TarifaResponse;
import com.marillia.milebag.business.tarifa.service.TabelaTarifaService;

/**
 * Endpoints de tarifa (HU09) - ver contracts/tarifas-api.md. Restrito ao
 * perfil administrador do tenant (SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/tarifas")
public class TabelaTarifaController {

    private final TabelaTarifaService tabelaTarifaService;

    public TabelaTarifaController(TabelaTarifaService tabelaTarifaService) {
        this.tabelaTarifaService = tabelaTarifaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarifaResponse criar(@RequestBody CriarTarifaRequest request) {
        return TarifaResponse.de(tabelaTarifaService.criar(request));
    }

    @GetMapping
    public List<TarifaResponse> listar(
            @RequestParam(required = false) EscopoTarifa escopo,
            @RequestParam(required = false) UUID motoristaId,
            @RequestParam(required = false) String regiao) {
        return tabelaTarifaService.listar(escopo, motoristaId, regiao).stream().map(TarifaResponse::de).toList();
    }

    @GetMapping("/{id}")
    public TarifaResponse buscar(@PathVariable UUID id) {
        return TarifaResponse.de(tabelaTarifaService.buscar(id));
    }

    @PutMapping("/{id}")
    public TarifaResponse atualizar(@PathVariable UUID id, @RequestBody AtualizarTarifaRequest request) {
        return TarifaResponse.de(tabelaTarifaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id) {
        tabelaTarifaService.remover(id);
    }
}
