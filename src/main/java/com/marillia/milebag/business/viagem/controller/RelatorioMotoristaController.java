package com.marillia.milebag.business.viagem.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.viagem.model.RelatorioQuinzenalResponse;
import com.marillia.milebag.business.viagem.service.RelatorioMotoristaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Relatorio quinzenal do motorista (HU17) - ver contracts/relatorio-motorista-api.md. */
@RestController
public class RelatorioMotoristaController {

    private final RelatorioMotoristaService relatorioMotoristaService;

    public RelatorioMotoristaController(RelatorioMotoristaService relatorioMotoristaService) {
        this.relatorioMotoristaService = relatorioMotoristaService;
    }

    @GetMapping("/api/v1/motorista/relatorio-quinzenal")
    public RelatorioQuinzenalResponse relatorio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodoFim,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return relatorioMotoristaService.consultarRelatorio(usuario.userId(), periodoInicio, periodoFim);
    }
}
