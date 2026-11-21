package com.marillia.milebag.business.segundavia.controller;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.segundavia.model.EnviarSegundaViaRequest;
import com.marillia.milebag.business.segundavia.service.SegundaViaBdoService;
import com.marillia.milebag.business.segundavia.service.SegundaViaBdoService.ArquivoPdf;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/**
 * Segunda via do BDO em PDF (V50). O motorista pela entrega que esta com ele ({@code /api/v1/entregas/**}); a empresa
 * por qualquer BDO ({@code /api/v1/gestao/**}, so o administrador do tenant).
 */
@RestController
public class SegundaViaBdoController {

    private final SegundaViaBdoService segundaViaBdoService;

    public SegundaViaBdoController(SegundaViaBdoService segundaViaBdoService) {
        this.segundaViaBdoService = segundaViaBdoService;
    }

    @GetMapping("/api/v1/entregas/{entregaId}/segunda-via-bdo")
    public ResponseEntity<byte[]> pdfDoMotorista(@PathVariable UUID entregaId, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return pdf(segundaViaBdoService.pdfDoMotorista(entregaId, usuario.userId()));
    }

    @PostMapping("/api/v1/entregas/{entregaId}/segunda-via-bdo/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarDoMotorista(@PathVariable UUID entregaId, @RequestBody EnviarSegundaViaRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        segundaViaBdoService.enviarDoMotorista(entregaId, usuario.userId(), request.email());
    }

    @GetMapping("/api/v1/gestao/bdos/{bdoId}/segunda-via")
    public ResponseEntity<byte[]> pdfDaEmpresa(@PathVariable UUID bdoId) {
        return pdf(segundaViaBdoService.pdfDaEmpresa(bdoId));
    }

    @PostMapping("/api/v1/gestao/bdos/{bdoId}/segunda-via/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarDaEmpresa(@PathVariable UUID bdoId, @RequestBody EnviarSegundaViaRequest request,
            @AuthenticationPrincipal AuthenticatedUser usuario) {
        segundaViaBdoService.enviarDaEmpresa(bdoId, usuario.userId(), request.email());
    }

    /** Sem cache: o PDF tem dados pessoais do passageiro e nao deve ficar guardado por proxies. */
    private static ResponseEntity<byte[]> pdf(ArquivoPdf arquivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(arquivo.nomeArquivo()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(arquivo.conteudo());
    }
}
