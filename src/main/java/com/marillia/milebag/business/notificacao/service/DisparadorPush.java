package com.marillia.milebag.business.notificacao.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marillia.milebag.business.notificacao.model.InscricaoPush;

/**
 * Envia um mesmo aviso a uma lista de aparelhos inscritos (HU32): remove a inscricao que o servico de push diz nao
 * existir mais (404/410) e nunca lanca - falha num aparelho nao impede os outros nem quem pediu o aviso. Comum ao
 * aviso de BDO novo (todos os motoristas) e ao de BDO cancelado (so o motorista da entrega, V47).
 */
@Component
public class DisparadorPush {

    private static final Logger log = LoggerFactory.getLogger(DisparadorPush.class);

    private final InscricaoPushService inscricaoPushService;
    private final EnviadorPush enviadorPush;

    public DisparadorPush(InscricaoPushService inscricaoPushService, EnviadorPush enviadorPush) {
        this.inscricaoPushService = inscricaoPushService;
        this.enviadorPush = enviadorPush;
    }

    public boolean habilitado() {
        return enviadorPush.habilitado();
    }

    /** {@code assunto} so identifica o aviso no log. Devolve quantos aparelhos aceitaram. */
    public int disparar(List<InscricaoPush> inscricoes, String conteudo, String assunto) {
        int enviados = 0;
        for (InscricaoPush inscricao : inscricoes) {
            try {
                int status = enviadorPush.enviar(inscricao, conteudo);
                if (status == 404 || status == 410) {
                    inscricaoPushService.removerExpirada(inscricao.getId());
                } else if (status >= 400) {
                    log.warn("Servico de push recusou o aviso {} (HTTP {}) para a inscricao {}.", assunto, status, inscricao.getId());
                } else {
                    enviados++;
                }
            } catch (Exception e) {
                log.warn("Falha ao enviar o aviso {} para a inscricao {} - seguindo com as demais.", assunto, inscricao.getId(), e);
            }
        }
        log.info("Aviso {} enviado a {} de {} aparelho(s).", assunto, enviados, inscricoes.size());
        return enviados;
    }
}
