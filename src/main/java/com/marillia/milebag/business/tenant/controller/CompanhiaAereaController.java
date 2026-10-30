package com.marillia.milebag.business.tenant.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.service.TenantService;

/** Companhias aereas (HU33) - ver contracts/companhias-api.md. Restrito ao administrador da plataforma (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/plataforma/companhias-aereas")
public class CompanhiaAereaController {

    public record CadastroCompanhiaRequest(String identificador, String nome, String codigoIata, String emailAdministrador) {
    }

    public record CompanhiaAereaResponse(UUID id, String identificador, String nome, String codigoIata, Instant criadoEm) {
        static CompanhiaAereaResponse de(Tenant companhia) {
            return new CompanhiaAereaResponse(
                    companhia.getId(), companhia.getIdentificadorEmpresa(), companhia.getNome(), companhia.getCodigoIata(), companhia.getCriadoEm());
        }
    }

    private final TenantService tenantService;

    public CompanhiaAereaController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public List<CompanhiaAereaResponse> listar() {
        return tenantService.listarCompanhias().stream().map(CompanhiaAereaResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanhiaAereaResponse cadastrar(@RequestBody CadastroCompanhiaRequest request) {
        return CompanhiaAereaResponse.de(
                tenantService.cadastrarCompanhia(request.identificador(), request.nome(), request.codigoIata(), request.emailAdministrador()));
    }
}
