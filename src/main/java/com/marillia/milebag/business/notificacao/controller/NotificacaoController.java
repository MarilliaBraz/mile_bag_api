package com.marillia.milebag.business.notificacao.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.notificacao.model.ChavePublicaResponse;
import com.marillia.milebag.business.notificacao.model.InscreverPushRequest;
import com.marillia.milebag.business.notificacao.model.RemoverInscricaoRequest;
import com.marillia.milebag.business.notificacao.service.EnviadorPush;
import com.marillia.milebag.business.notificacao.service.InscricaoPushService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Notificacoes do aparelho do motorista (HU32) - ver specs/HU32-aviso-bdo-novo/contracts/notificacoes-api.md. */
@RestController
@RequestMapping("/api/v1/notificacoes")
public class NotificacaoController {

    private final InscricaoPushService inscricaoPushService;
    private final EnviadorPush enviadorPush;

    public NotificacaoController(InscricaoPushService inscricaoPushService, EnviadorPush enviadorPush) {
        this.inscricaoPushService = inscricaoPushService;
        this.enviadorPush = enviadorPush;
    }

    /** 204 quando o push esta desligado no ambiente. */
    @GetMapping("/chave-publica")
    public ResponseEntity<ChavePublicaResponse> chavePublica() {
        return enviadorPush.habilitado()
                ? ResponseEntity.ok(new ChavePublicaResponse(enviadorPush.chavePublica()))
                : ResponseEntity.noContent().build();
    }

    @PostMapping("/inscricoes")
    public ResponseEntity<Void> inscrever(@RequestBody InscreverPushRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        inscricaoPushService.inscrever(usuario.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inscricoes/remover")
    public ResponseEntity<Void> remover(@RequestBody RemoverInscricaoRequest request) {
        inscricaoPushService.remover(request == null ? null : request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
