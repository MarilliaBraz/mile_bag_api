package com.marillia.milebag.business.companhiaaerea.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.companhiaaerea.service.CompanhiaAereaService;

/** Lista de companhias aereas para a busca pelo nome em Configuracoes (V53). Restrito ao administrador (SecurityConfig). */
@RestController
public class CompanhiaAereaCatalogoController {

    public record CompanhiaAereaResponse(String codigoIata, String codigoIcao, String nome, String pais) {
    }

    private final CompanhiaAereaService companhiaAereaService;

    public CompanhiaAereaCatalogoController(CompanhiaAereaService companhiaAereaService) {
        this.companhiaAereaService = companhiaAereaService;
    }

    @GetMapping("/api/v1/catalogo/companhias-aereas")
    public List<CompanhiaAereaResponse> listar() {
        return companhiaAereaService.listar().stream()
                .map(c -> new CompanhiaAereaResponse(c.getCodigoIata(), c.getCodigoIcao(), c.getNome(), c.getPais()))
                .toList();
    }
}
