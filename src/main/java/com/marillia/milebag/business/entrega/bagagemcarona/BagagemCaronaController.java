package com.marillia.milebag.business.entrega.bagagemcarona;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de vinculo de bagagem carona (HU05) - ver contracts/bagagem-carona-api.md. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/bagagens-carona")
public class BagagemCaronaController {

    private final BagagemCaronaService bagagemCaronaService;

    public BagagemCaronaController(BagagemCaronaService bagagemCaronaService) {
        this.bagagemCaronaService = bagagemCaronaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BagagemCaronaResponse vincular(@PathVariable UUID entregaId, @RequestBody VincularBagagemCaronaRequest request) {
        return BagagemCaronaResponse.de(bagagemCaronaService.vincular(entregaId, request.bdoId()));
    }

    @DeleteMapping("/{bagagemCaronaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@PathVariable UUID entregaId, @PathVariable UUID bagagemCaronaId) {
        bagagemCaronaService.desvincular(entregaId, bagagemCaronaId);
    }

    @GetMapping
    public List<BagagemCaronaResponse> listar(@PathVariable UUID entregaId) {
        return bagagemCaronaService.listarPorEntrega(entregaId).stream().map(BagagemCaronaResponse::de).toList();
    }
}
