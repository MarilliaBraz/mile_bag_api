package com.marillia.milebag.business.entrega.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.entrega.model.EntregaEncontradaResponse;
import com.marillia.milebag.business.entrega.service.BuscaBdoMotoristaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Item 4c: busca do motorista pelo numero do BDO, so nas entregas dele. */
@RestController
public class BuscaBdoMotoristaController {

    private final BuscaBdoMotoristaService buscaBdoMotoristaService;

    public BuscaBdoMotoristaController(BuscaBdoMotoristaService buscaBdoMotoristaService) {
        this.buscaBdoMotoristaService = buscaBdoMotoristaService;
    }

    @GetMapping("/api/v1/motorista/entregas")
    public List<EntregaEncontradaResponse> buscar(@RequestParam String bdoNumero, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return buscaBdoMotoristaService.buscar(usuario.userId(), bdoNumero);
    }
}
