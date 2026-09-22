package com.marillia.milebag.business.configuracao.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.configuracao.model.IdentidadeVisualTenantResponse;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService.LogotipoArquivo;

/**
 * Identidade visual do tenant (HU13) - ver contracts/identidade-visual-api.md.
 * Restrito ao perfil administrador do tenant (SecurityConfig); tenantId
 * sempre resolvido do token, nunca recebido como parametro.
 */
@RestController
public class IdentidadeVisualTenantController {

    private final IdentidadeVisualTenantService identidadeVisualTenantService;

    public IdentidadeVisualTenantController(IdentidadeVisualTenantService identidadeVisualTenantService) {
        this.identidadeVisualTenantService = identidadeVisualTenantService;
    }

    @GetMapping("/api/v1/identidade-visual")
    public IdentidadeVisualTenantResponse buscar() {
        return identidadeVisualTenantService.buscar()
                .map(IdentidadeVisualTenantResponse::de)
                .orElseGet(IdentidadeVisualTenantResponse::vazia);
    }

    @PutMapping(value = "/api/v1/identidade-visual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentidadeVisualTenantResponse salvar(
            @RequestParam(value = "logotipo", required = false) MultipartFile logotipo,
            @RequestParam(value = "cabecalho", required = false) String cabecalho) {
        return IdentidadeVisualTenantResponse.de(identidadeVisualTenantService.salvar(logotipo, cabecalho));
    }

    /**
     * Nao definido no contrato desta HU (que so descreve logotipoUrl como
     * string) - endpoint minimo necessario para essa URL ser de fato
     * servivel pelo front-end (preview em T016), mesma logica de
     * autorizacao dos dois endpoints acima (prefixo comum no SecurityConfig).
     */
    @GetMapping(value = "/api/v1/identidade-visual/logotipo")
    public ResponseEntity<byte[]> logotipo() {
        LogotipoArquivo arquivo = identidadeVisualTenantService.carregarLogotipo();
        MediaType tipo = switch (arquivo.extensao()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok().contentType(tipo).body(arquivo.conteudo());
    }
}
