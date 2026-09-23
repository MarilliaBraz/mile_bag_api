package com.marillia.milebag.business.tenant.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tenant.model.CadastroTenantRequest;
import com.marillia.milebag.business.tenant.model.TenantResponse;
import com.marillia.milebag.business.tenant.service.ConviteAdministradorService;
import com.marillia.milebag.business.tenant.service.TenantService;

/**
 * Cadastro de empresas assinantes (HU14) - ver contracts/tenants-api.md.
 * Restrito ao perfil administrador da plataforma (SecurityConfig) - nao
 * confundir com administrador de um tenant (RD12).
 */
@RestController
@RequestMapping("/api/v1/plataforma/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final ConviteAdministradorService conviteAdministradorService;

    public TenantController(TenantService tenantService, ConviteAdministradorService conviteAdministradorService) {
        this.tenantService = tenantService;
        this.conviteAdministradorService = conviteAdministradorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse cadastrar(@RequestBody CadastroTenantRequest request) {
        return TenantResponse.de(tenantService.cadastrar(
                request.identificadorEmpresa(), request.nome(), request.planoId(), request.emailAdministrador()));
    }

    @GetMapping("/{id}")
    public TenantResponse buscar(@PathVariable UUID id) {
        return TenantResponse.de(tenantService.buscar(id));
    }

    /** HU15 (contracts/convite-api.md): reenvio restrito ao mesmo perfil desta classe (SecurityConfig). */
    @PostMapping("/{id}/convites/reenviar")
    @ResponseStatus(HttpStatus.CREATED)
    public void reenviarConvite(@PathVariable UUID id) {
        conviteAdministradorService.reenviar(id);
    }
}
