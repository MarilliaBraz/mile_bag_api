package com.marillia.milebag.business.bagagemencontrada.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.bagagemencontrada.service.AeroportoService;

/** Aeroportos brasileiros para a escolha manual no registro de bagagem (HU34). Restrito aos perfis da companhia (SecurityConfig). */
@RestController
public class AeroportoController {

    public record AeroportoResponse(String codigo, String nome, String cidade, String uf) {
    }

    private final AeroportoService aeroportoService;

    public AeroportoController(AeroportoService aeroportoService) {
        this.aeroportoService = aeroportoService;
    }

    @GetMapping("/api/v1/aeroportos")
    public List<AeroportoResponse> listar() {
        return aeroportoService.listar().stream().map(a -> new AeroportoResponse(a.getCodigo(), a.getNome(), a.getCidade(), a.getUf())).toList();
    }
}
