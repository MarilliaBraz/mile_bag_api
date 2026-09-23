package com.marillia.milebag.business.tenant.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tenant.model.CriarPlanoRequest;
import com.marillia.milebag.business.tenant.model.PlanoAssinaturaListItemResponse;
import com.marillia.milebag.business.tenant.model.PlanoAssinaturaResponse;
import com.marillia.milebag.business.tenant.model.PlanoVigenteResponse;
import com.marillia.milebag.business.tenant.service.PlanoAssinaturaService;
import com.marillia.milebag.business.tenant.service.TenantPlanoService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/**
 * CRUD de planos de assinatura (HU16) - ver contracts/planos-api.md. `GET
 * /api/v1/plano` e a excecao de perfil desta classe: acessivel ao
 * administrador do proprio tenant, nao so ao administrador da plataforma
 * (SecurityConfig) - por isso nao vive sob {@code /plataforma/planos}.
 */
@RestController
public class PlanoAssinaturaController {

    private final PlanoAssinaturaService planoAssinaturaService;
    private final TenantPlanoService tenantPlanoService;

    public PlanoAssinaturaController(PlanoAssinaturaService planoAssinaturaService, TenantPlanoService tenantPlanoService) {
        this.planoAssinaturaService = planoAssinaturaService;
        this.tenantPlanoService = tenantPlanoService;
    }

    @PostMapping("/api/v1/plataforma/planos")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanoAssinaturaResponse criar(@RequestBody CriarPlanoRequest request) {
        var plano = planoAssinaturaService.criar(request.nome(), request.limiteMotoristasAtivos(), request.limiteVolumeMensalBdos());
        return PlanoAssinaturaResponse.de(plano);
    }

    @GetMapping("/api/v1/plataforma/planos")
    public List<PlanoAssinaturaListItemResponse> listar() {
        return planoAssinaturaService.listar();
    }

    @DeleteMapping("/api/v1/plataforma/planos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id) {
        planoAssinaturaService.remover(id);
    }

    /** Tenant sempre resolvido a partir do claim do token, nunca de parametro do cliente (mesmo padrao de HU09-HU12). */
    @GetMapping("/api/v1/plano")
    public PlanoVigenteResponse consultarPlanoDoTenant(@AuthenticationPrincipal AuthenticatedUser usuario) {
        var consulta = tenantPlanoService.consultarVigente(usuario.tenantId());
        return new PlanoVigenteResponse(PlanoAssinaturaResponse.de(consulta.planoVigente()), consulta.usoAtual(), consulta.dentroDoLimite());
    }
}
