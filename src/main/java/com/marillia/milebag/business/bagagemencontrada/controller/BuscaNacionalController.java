package com.marillia.milebag.business.bagagemencontrada.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.bagagemencontrada.model.BuscaNacionalResponse;
import com.marillia.milebag.business.bagagemencontrada.service.BuscaNacionalService;

/**
 * Busca nacional por etiqueta (HU36, RD22) - ver specs/HU36-consulta-bagagens/contracts/consulta-bagagens-api.md. Restrita
 * aos perfis da companhia aerea ({@code /api/v1/companhia/**} no SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/companhia/busca-nacional")
public class BuscaNacionalController {

    private final BuscaNacionalService service;

    public BuscaNacionalController(BuscaNacionalService service) {
        this.service = service;
    }

    @GetMapping
    public List<BuscaNacionalResponse> buscar(@RequestParam(value = "etiqueta", required = false) String etiqueta) {
        return service.buscar(etiqueta);
    }
}
