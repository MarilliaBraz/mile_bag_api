package com.marillia.milebag.business.viagem.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.viagem.model.CheckpointResponse;
import com.marillia.milebag.business.viagem.model.EncerrarTrajetoRequest;
import com.marillia.milebag.business.viagem.model.IniciarViagemRequest;
import com.marillia.milebag.business.viagem.model.RegistrarCheckpointRequest;
import com.marillia.milebag.business.viagem.model.TrajetoDetalhadoResponse;
import com.marillia.milebag.business.viagem.model.TrajetoIniciadoResponse;
import com.marillia.milebag.business.viagem.service.ViagemService;

/** Endpoints do trajeto da viagem (HU04) - ver contracts/viagem-api.md. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/viagem")
public class ViagemController {

    private final ViagemService viagemService;

    public ViagemController(ViagemService viagemService) {
        this.viagemService = viagemService;
    }

    @PostMapping("/iniciar")
    @ResponseStatus(HttpStatus.CREATED)
    public TrajetoIniciadoResponse iniciar(@PathVariable UUID entregaId, @RequestBody(required = false) IniciarViagemRequest request) {
        return viagemService.iniciarViagem(entregaId, request);
    }

    @GetMapping
    public TrajetoDetalhadoResponse consultar(@PathVariable UUID entregaId) {
        return viagemService.consultar(entregaId);
    }

    @PostMapping("/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckpointResponse registrarCheckpoint(@PathVariable UUID entregaId, @RequestBody RegistrarCheckpointRequest request) {
        return viagemService.registrarCheckpoint(entregaId, request);
    }

    @PostMapping("/encerrar")
    public TrajetoDetalhadoResponse encerrar(@PathVariable UUID entregaId, @RequestBody(required = false) EncerrarTrajetoRequest request) {
        return viagemService.encerrarTrajeto(entregaId, request);
    }
}
