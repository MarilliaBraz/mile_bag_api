package com.marillia.milebag.business.ocorrencia.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.ocorrencia.model.OcorrenciaResponse;
import com.marillia.milebag.business.ocorrencia.model.RegistrarOcorrenciaRequest;
import com.marillia.milebag.business.ocorrencia.service.OcorrenciaService;

/** Endpoints de ocorrencia de nao entrega (HU07) - ver contracts/ocorrencia-api.md. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/ocorrencias")
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;

    public OcorrenciaController(OcorrenciaService ocorrenciaService) {
        this.ocorrenciaService = ocorrenciaService;
    }

    /** 201 para uma ocorrencia nova; 200 quando e o reenvio do mesmo {@code idLocal} (nada foi criado). */
    @PostMapping
    public ResponseEntity<OcorrenciaResponse> registrar(@PathVariable UUID entregaId, @RequestBody RegistrarOcorrenciaRequest request) {
        var resultado = ocorrenciaService.registrarComResultado(
                entregaId, request.motivoOcorrenciaId(), request.descricaoAdicional(), request.idLocal());
        return ResponseEntity.status(resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.ocorrencia());
    }

    @GetMapping
    public List<OcorrenciaResponse> listar(@PathVariable UUID entregaId) {
        return ocorrenciaService.listarPorEntrega(entregaId);
    }
}
