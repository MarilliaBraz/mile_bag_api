package com.marillia.milebag.business.acompanhamento.controller;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.acompanhamento.model.AcompanhamentoResponse;
import com.marillia.milebag.business.acompanhamento.service.AcompanhamentoService;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService.LogotipoArquivo;
import com.marillia.milebag.business.viagem.model.LinkAcompanhamento;
import com.marillia.milebag.core.config.security.TenantContext;

/**
 * Pagina publica "Acompanhe sua bagagem" (HU31) - ver contracts/acompanhamento-api.md. Publico (SecurityConfig):
 * o passageiro nao tem conta. Sem tenant no JWT, o tenant vem do proprio link (tabela sem RLS, V38) e e aplicado ao
 * {@link TenantContext} so durante a leitura - mesma estrategia de {@code TelegramWebhookController} (HU18).
 */
@RestController
@RequestMapping("/api/v1/acompanhamento/{codigo}")
public class AcompanhamentoController {

    private final AcompanhamentoService acompanhamentoService;

    public AcompanhamentoController(AcompanhamentoService acompanhamentoService) {
        this.acompanhamentoService = acompanhamentoService;
    }

    @GetMapping
    public AcompanhamentoResponse consultar(@PathVariable UUID codigo) {
        LinkAcompanhamento link = acompanhamentoService.resolver(codigo);
        return noTenantDoLink(link, () -> acompanhamentoService.consultar(link));
    }

    @GetMapping("/logotipo")
    public ResponseEntity<byte[]> logotipo(@PathVariable UUID codigo) {
        LinkAcompanhamento link = acompanhamentoService.resolver(codigo);
        LogotipoArquivo arquivo = noTenantDoLink(link, acompanhamentoService::logotipo);
        MediaType tipo = switch (arquivo.extensao()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok().contentType(tipo).body(arquivo.conteudo());
    }

    /** Restaura o tenant anterior (o de um usuario logado que abriu o link, se houver) ao sair. */
    private static <T> T noTenantDoLink(LinkAcompanhamento link, Supplier<T> leitura) {
        UUID anterior = TenantContext.get();
        TenantContext.set(link.getTenantId());
        try {
            return leitura.get();
        } finally {
            if (anterior == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(anterior);
            }
        }
    }
}
