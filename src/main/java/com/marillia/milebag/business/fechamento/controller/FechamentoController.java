package com.marillia.milebag.business.fechamento.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.fechamento.model.FechamentoDetalheResponse;
import com.marillia.milebag.business.fechamento.model.FechamentoResponse;
import com.marillia.milebag.business.fechamento.model.GerarFechamentoRequest;
import com.marillia.milebag.business.fechamento.service.FechamentoService;
import com.marillia.milebag.business.fechamento.service.RelatorioFechamentoService;

/** Endpoints de fechamento (HU12) - ver contracts/fechamento-api.md. Restrito ao perfil administrador do tenant. */
@RestController
@RequestMapping("/api/v1/fechamentos")
public class FechamentoController {

    private final FechamentoService fechamentoService;
    private final RelatorioFechamentoService relatorioFechamentoService;

    public FechamentoController(FechamentoService fechamentoService, RelatorioFechamentoService relatorioFechamentoService) {
        this.fechamentoService = fechamentoService;
        this.relatorioFechamentoService = relatorioFechamentoService;
    }

    @PostMapping
    public ResponseEntity<FechamentoResponse> gerar(@RequestBody GerarFechamentoRequest request) {
        var resultado = fechamentoService.gerar(request.periodoInicio(), request.periodoFim());
        HttpStatus status = resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resultado.resumo());
    }

    @GetMapping
    public List<FechamentoResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return fechamentoService.listar(de, ate);
    }

    @GetMapping("/{id}")
    public FechamentoDetalheResponse detalhar(@PathVariable UUID id) {
        return fechamentoService.detalhar(id);
    }

    @GetMapping(value = "/{id}/relatorio-pagamento-entregas.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public byte[] relatorioPagamentoEntregas(@PathVariable UUID id) {
        return relatorioFechamentoService.gerarRelatorioPagamentoEntregas(id);
    }

    @GetMapping(value = "/{id}/relatorio-reembolso-pedagios.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public byte[] relatorioReembolsoPedagios(@PathVariable UUID id) {
        return relatorioFechamentoService.gerarRelatorioReembolsoPedagios(id);
    }
}
