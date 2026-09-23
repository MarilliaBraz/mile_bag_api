package com.marillia.milebag.business.mensagem.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.mensagem.model.ConversaResponse;
import com.marillia.milebag.business.mensagem.model.EnviarMensagemRequest;
import com.marillia.milebag.business.mensagem.model.MensagemResponse;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.core.config.security.AuthenticatedUser;

/** Conversa por entrega com o passageiro (HU18) - ver contracts/mensagens-api.md. */
@RestController
@RequestMapping("/api/v1/entregas/{entregaId}/mensagens")
public class MensagemController {

    private final MensagemService mensagemService;

    public MensagemController(MensagemService mensagemService) {
        this.mensagemService = mensagemService;
    }

    @GetMapping
    public ConversaResponse consultar(@PathVariable UUID entregaId, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return mensagemService.consultarConversa(entregaId, usuario.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemResponse enviar(
            @PathVariable UUID entregaId, @RequestBody EnviarMensagemRequest request, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return mensagemService.enviarManual(entregaId, usuario.userId(), request.conteudo());
    }
}
