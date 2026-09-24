package com.marillia.milebag.business.pedagio.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.pedagio.model.AdicionarValorRequest;
import com.marillia.milebag.business.pedagio.model.DefinirLocalizacaoRequest;
import com.marillia.milebag.business.pedagio.model.CriarPracaPedagioRequest;
import com.marillia.milebag.business.pedagio.model.PracaPedagioResponse;
import com.marillia.milebag.business.pedagio.model.ValorPedagioResponse;
import com.marillia.milebag.business.pedagio.service.PracaPedagioService;

/**
 * Endpoints de pracas de pedagio (HU10) - ver contracts/pedagios-api.md.
 * Escrita restrita ao perfil administrador do tenant; leitura tambem
 * permitida ao motorista (SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/pracas-pedagio")
public class PracaPedagioController {

    private final PracaPedagioService pracaPedagioService;

    public PracaPedagioController(PracaPedagioService pracaPedagioService) {
        this.pracaPedagioService = pracaPedagioService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PracaPedagioResponse criar(@RequestBody CriarPracaPedagioRequest request) {
        return pracaPedagioService.criarPraca(request.nome(), request.valor(), request.vigenteDesde(), request.latitude(), request.longitude());
    }

    @GetMapping
    public List<PracaPedagioResponse> listar() {
        return pracaPedagioService.listarPracas();
    }

    /** HU24: localizacao da praca (ou as duas coordenadas nulas para remover) - e o que permite detectar a praca nas rotas. */
    @PutMapping("/{id}/localizacao")
    public PracaPedagioResponse definirLocalizacao(@PathVariable UUID id, @RequestBody DefinirLocalizacaoRequest request) {
        return pracaPedagioService.definirLocalizacao(id, request.latitude(), request.longitude());
    }

    @GetMapping("/{id}/valores")
    public List<ValorPedagioResponse> historico(@PathVariable UUID id) {
        return pracaPedagioService.historico(id).stream().map(ValorPedagioResponse::de).toList();
    }

    @PostMapping("/{id}/valores")
    @ResponseStatus(HttpStatus.CREATED)
    public ValorPedagioResponse adicionarValor(@PathVariable UUID id, @RequestBody AdicionarValorRequest request) {
        return ValorPedagioResponse.de(pracaPedagioService.adicionarValor(id, request.valor(), request.vigenteDesde()));
    }

    @GetMapping("/{id}/valor-em")
    public ValorPedagioResponse valorEm(@PathVariable UUID id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ValorPedagioResponse.de(pracaPedagioService.valorVigenteEm(id, data));
    }
}
