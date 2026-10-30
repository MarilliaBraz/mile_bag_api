package com.marillia.milebag.business.demonstracao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.demonstracao.model.PedidoDemonstracaoRequest;
import com.marillia.milebag.business.demonstracao.service.DemonstracaoService;

import jakarta.servlet.http.HttpServletRequest;

/** Pedido de demonstracao da pagina institucional (HU38) - publico, sem token. */
@RestController
@RequestMapping("/api/v1/publico/demonstracao")
public class DemonstracaoController {

    private final DemonstracaoService demonstracaoService;

    public DemonstracaoController(DemonstracaoService demonstracaoService) {
        this.demonstracaoService = demonstracaoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pedir(@RequestBody PedidoDemonstracaoRequest pedido, HttpServletRequest request) {
        demonstracaoService.pedir(pedido, ipDe(request));
    }

    /** O Caddy coloca o IP do visitante em X-Forwarded-For; sem proxy, vale o endereco da conexao. */
    static String ipDe(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
