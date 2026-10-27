package com.marillia.milebag.business.emergencia.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.emergencia.model.AcionarAlertaRequest;
import com.marillia.milebag.business.emergencia.model.AlertaEmergenciaResponse;
import com.marillia.milebag.business.emergencia.model.AtenderAlertaRequest;
import com.marillia.milebag.business.emergencia.model.StatusAlertaEmergencia;
import com.marillia.milebag.business.emergencia.service.AlertaEmergenciaService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/**
 * Alerta de emergencia (HU30) - ver contracts/emergencia-api.md. Perfis por
 * rota em {@code SecurityConfig}: acionar, consultar o proprio e cancelar
 * sao do motorista; listar e atender, do administrador do tenant.
 */
@RestController
@RequestMapping("/api/v1/emergencias")
public class AlertaEmergenciaController {

    private final AlertaEmergenciaService alertaEmergenciaService;

    public AlertaEmergenciaController(AlertaEmergenciaService alertaEmergenciaService) {
        this.alertaEmergenciaService = alertaEmergenciaService;
    }

    /** 201 para um alerta novo; 200 quando o motorista ja tinha um aberto (o mesmo e devolvido). */
    @PostMapping
    public ResponseEntity<AlertaEmergenciaResponse> acionar(
            @RequestBody(required = false) AcionarAlertaRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        Double latitude = request == null ? null : request.latitude();
        Double longitude = request == null ? null : request.longitude();
        AlertaEmergenciaResponse alerta = alertaEmergenciaService.acionar(usuario.userId(), latitude, longitude);
        return ResponseEntity.status(alerta.novo() ? HttpStatus.CREATED : HttpStatus.OK).body(alerta);
    }

    /** 204 quando o motorista nao tem alerta aberto. */
    @GetMapping("/minha")
    public ResponseEntity<AlertaEmergenciaResponse> consultarMeuAberto(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return alertaEmergenciaService.consultarAbertoDoMotorista(usuario.userId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/cancelar")
    public AlertaEmergenciaResponse cancelar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return alertaEmergenciaService.cancelar(id, usuario.userId());
    }

    @GetMapping
    public List<AlertaEmergenciaResponse> listar(@RequestParam(required = false) StatusAlertaEmergencia status) {
        return alertaEmergenciaService.listar(status);
    }

    @PostMapping("/{id}/atender")
    public AlertaEmergenciaResponse atender(
            @PathVariable UUID id,
            @RequestBody(required = false) AtenderAlertaRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        return alertaEmergenciaService.atender(id, usuario.userId(), request == null ? null : request.observacao());
    }
}
