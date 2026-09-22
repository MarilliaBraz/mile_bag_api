package com.marillia.milebag.business.ocorrencia.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.ocorrencia.model.MotivoOcorrenciaResponse;
import com.marillia.milebag.business.ocorrencia.repository.MotivoOcorrenciaRepository;

/**
 * Ver contracts/ocorrencia-api.md. O {@code tenantId} do path nunca e usado
 * para escopar a consulta - a mesma regra do resto da API: o tenant vem
 * sempre de {@code TenantContext} (JWT), nunca de um valor vindo do
 * cliente. A politica de Row Level Security ja faz esse filtro sozinha.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/motivos-ocorrencia")
public class MotivoOcorrenciaController {

    private final MotivoOcorrenciaRepository motivoOcorrenciaRepository;

    public MotivoOcorrenciaController(MotivoOcorrenciaRepository motivoOcorrenciaRepository) {
        this.motivoOcorrenciaRepository = motivoOcorrenciaRepository;
    }

    @GetMapping
    public List<MotivoOcorrenciaResponse> listar(@PathVariable UUID tenantId) {
        return motivoOcorrenciaRepository.findByAtivoTrue().stream().map(MotivoOcorrenciaResponse::de).toList();
    }
}
