package com.marillia.milebag.business.mensagem.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.mensagem.model.TelegramUpdate;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.tenant.service.TenantService;
import com.marillia.milebag.core.config.TelegramProperties;
import com.marillia.milebag.core.config.security.TenantContext;

/**
 * Webhook de entrada da Bot API do Telegram (HU18) - ver
 * contracts/mensagens-api.md. Sem JWT (chamado pelos servidores do
 * Telegram, nao pelo front-end do motorista) - autenticado pelo cabecalho
 * {@code X-Telegram-Bot-Api-Secret-Token}, configurado junto ao {@code
 * setWebhook}.
 *
 * <p>Sem claim de tenant nesta via de entrada: o tenant dono do recurso
 * (entrega, no {@code /start}; vinculo existente, na mensagem livre) e
 * resolvido tentando cada tenant conhecido (mesma estrategia de {@code
 * BdoEmailListener}/{@code TenantEmailBdo}, HU02, adaptada aqui via {@code
 * TenantService.listarTodosOsIds} porque a chave de entrada nao e um
 * endereco fixo por tenant).</p>
 */
@RestController
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String COMANDO_START = "/start";

    private final TelegramProperties telegramProperties;
    private final TenantService tenantService;
    private final EntregaService entregaService;
    private final MensagemService mensagemService;

    public TelegramWebhookController(
            TelegramProperties telegramProperties, TenantService tenantService, EntregaService entregaService,
            MensagemService mensagemService) {
        this.telegramProperties = telegramProperties;
        this.tenantService = tenantService;
        this.entregaService = entregaService;
        this.mensagemService = mensagemService;
    }

    @PostMapping("/api/v1/webhooks/telegram")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretRecebido,
            @RequestBody TelegramUpdate update) {
        if (secretRecebido == null || !secretRecebido.equals(telegramProperties.webhookSecret())) {
            log.warn("Webhook do Telegram recebido com X-Telegram-Bot-Api-Secret-Token invalido - descartado.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (update.message() == null || update.message().text() == null) {
            return ResponseEntity.ok().build();
        }

        long chatId = update.message().chat().id();
        String texto = update.message().text().trim();
        if (texto.startsWith(COMANDO_START)) {
            processarStart(chatId, texto);
        } else {
            processarMensagemLivre(chatId, texto);
        }

        // Response 200 sempre (contracts/mensagens-api.md) - evita reprocessamento automatico da atualizacao pelo Telegram.
        return ResponseEntity.ok().build();
    }

    private void processarStart(long chatId, String texto) {
        String payload = texto.length() > COMANDO_START.length() ? texto.substring(COMANDO_START.length()).trim() : "";
        UUID entregaId;
        try {
            entregaId = UUID.fromString(payload);
        } catch (IllegalArgumentException e) {
            log.warn("/start recebido do chat {} com payload invalido ('{}') - ignorado.", chatId, payload);
            return;
        }

        for (UUID tenantId : tenantService.listarTodosOsIds()) {
            TenantContext.set(tenantId);
            try {
                if (entregaService.buscarPorId(entregaId).isPresent()) {
                    mensagemService.confirmarVinculoTelegram(entregaId, chatId);
                    return;
                }
            } finally {
                TenantContext.clear();
            }
        }
        log.warn("/start recebido do chat {} para uma entrega nao encontrada em nenhum tenant ({}).", chatId, entregaId);
    }

    private void processarMensagemLivre(long chatId, String texto) {
        for (UUID tenantId : tenantService.listarTodosOsIds()) {
            TenantContext.set(tenantId);
            try {
                if (mensagemService.processarAtualizacaoTelegram(chatId, texto)) {
                    return;
                }
            } finally {
                TenantContext.clear();
            }
        }
        log.warn("Mensagem livre recebida do chat {} sem vinculo conhecido em nenhum tenant - ignorada.", chatId);
    }
}
