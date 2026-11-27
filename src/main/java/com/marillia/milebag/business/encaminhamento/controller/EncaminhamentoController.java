package com.marillia.milebag.business.encaminhamento.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.encaminhamento.model.EncaminharBagagemRequest;
import com.marillia.milebag.business.encaminhamento.model.EntregaParceiroResponse;
import com.marillia.milebag.business.encaminhamento.service.EncaminhamentoService;
import com.marillia.milebag.business.encaminhamento.service.EntregaParceiroService;
import com.marillia.milebag.business.encaminhamento.service.ParceriaEntregaService;
import com.marillia.milebag.business.fotoentrega.service.FotoEntregaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Encaminhamento para entrega pela companhia (HU37) - ver specs/HU37-encaminhar-entrega/contracts/encaminhamento-api.md. */
@RestController
@RequestMapping("/api/v1/companhia")
public class EncaminhamentoController {

    private final EncaminhamentoService service;
    private final ParceriaEntregaService parceriaService;
    private final EntregaParceiroService entregaParceiroService;

    public EncaminhamentoController(EncaminhamentoService service, ParceriaEntregaService parceriaService,
            EntregaParceiroService entregaParceiroService) {
        this.service = service;
        this.parceriaService = parceriaService;
        this.entregaParceiroService = entregaParceiroService;
    }

    /** Fase 2 do painel: a entrega do parceiro desta bagagem (comprovante, ultima tentativa, contato e rastreio). */
    @GetMapping("/bagagens/{id}/entrega")
    public EntregaParceiroResponse entrega(@PathVariable UUID id) {
        return entregaParceiroService.consultar(id);
    }

    @GetMapping("/bagagens/{id}/entrega/fotos/{fotoId}")
    public ResponseEntity<byte[]> fotoDaEntrega(@PathVariable UUID id, @PathVariable UUID fotoId) {
        FotoEntregaService.Arquivo arquivo = entregaParceiroService.foto(id, fotoId);
        MediaType tipo = switch (arquivo.extensao()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok().contentType(tipo).body(arquivo.conteudo());
    }

    @GetMapping("/empresas-entrega")
    public List<ParceriaEntregaService.EmpresaEntrega> parceiras(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return parceriaService.parceirasDa(usuario.tenantId());
    }

    @PostMapping("/bagagens/{id}/encaminhamento")
    @ResponseStatus(HttpStatus.CREATED)
    public BagagemEncontradaResponse encaminhar(
            @PathVariable UUID id, @RequestBody EncaminharBagagemRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return service.encaminhar(id, request, usuario.userId());
    }

    @DeleteMapping("/bagagens/{id}/encaminhamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        service.cancelar(id, usuario.userId());
    }
}
