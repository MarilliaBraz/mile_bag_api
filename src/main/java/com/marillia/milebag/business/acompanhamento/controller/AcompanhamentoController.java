package com.marillia.milebag.business.acompanhamento.controller;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.acompanhamento.model.AcompanhamentoResponse;
import com.marillia.milebag.business.acompanhamento.model.AvaliarEntregaRequest;
import com.marillia.milebag.business.acompanhamento.model.EtapaAcompanhamento;
import com.marillia.milebag.business.acompanhamento.service.AcompanhamentoService;
import com.marillia.milebag.business.acompanhamento.service.AvaliacaoEntregaService;
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
    private final AvaliacaoEntregaService avaliacaoService;

    public AcompanhamentoController(AcompanhamentoService acompanhamentoService, AvaliacaoEntregaService avaliacaoService) {
        this.acompanhamentoService = acompanhamentoService;
        this.avaliacaoService = avaliacaoService;
    }

    @GetMapping
    public AcompanhamentoResponse consultar(@PathVariable UUID codigo) {
        LinkAcompanhamento link = acompanhamentoService.resolver(codigo);
        return noTenantDoLink(link, () -> {
            AcompanhamentoResponse resposta = acompanhamentoService.consultar(link);
            return resposta.etapa() == EtapaAcompanhamento.ENTREGUE ? resposta.comAvaliada(avaliacaoService.avaliada(link.getEntregaId())) : resposta;
        });
    }

    /** V65: pesquisa de satisfacao do passageiro, depois da entrega (uma por entrega). Publica, pelo link. */
    @PostMapping("/avaliacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void avaliar(@PathVariable UUID codigo, @RequestBody AvaliarEntregaRequest pedido) {
        LinkAcompanhamento link = acompanhamentoService.resolver(codigo);
        noTenantDoLink(link, () -> {
            avaliacaoService.registrar(link, pedido);
            return null;
        });
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
