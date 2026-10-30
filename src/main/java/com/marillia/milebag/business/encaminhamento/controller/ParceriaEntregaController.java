package com.marillia.milebag.business.encaminhamento.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.encaminhamento.service.ParceriaEntregaService;

/** Empresas parceiras de cada companhia (HU37, RD23). Restrito ao administrador da plataforma (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/plataforma/companhias-aereas/{id}/parceiras")
public class ParceriaEntregaController {

    public record DefinirParceirasRequest(List<UUID> empresas) {
    }

    private final ParceriaEntregaService service;

    public ParceriaEntregaController(ParceriaEntregaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParceriaEntregaService.EmpresaParceira> listar(@PathVariable UUID id) {
        return service.listarParaCompanhia(id);
    }

    @PutMapping
    public List<ParceriaEntregaService.EmpresaParceira> definir(@PathVariable UUID id, @RequestBody DefinirParceirasRequest request) {
        return service.definir(id, request.empresas());
    }
}
