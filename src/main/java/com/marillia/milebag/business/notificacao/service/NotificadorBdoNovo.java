package com.marillia.milebag.business.notificacao.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.bdo.model.BdoRecebidoPorEmail;
import com.marillia.milebag.business.notificacao.model.InscricaoPush;

/**
 * Avisa os aparelhos inscritos dos motoristas ativos do tenant quando um BDO chega por e-mail (HU32, RF25).
 *
 * <p>Depois do commit: so avisa de um BDO que de fato ficou registrado. Numa transacao nova, porque a do registro ja
 * terminou e a leitura das inscricoes precisa de transacao para a RLS valer (TenantAspect); o tenant continua no
 * {@code TenantContext} definido por {@code BdoEmailListener}. Nunca lanca: falha num aparelho nao impede os outros
 * nem o registro do BDO.</p>
 */
@Component
public class NotificadorBdoNovo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorBdoNovo.class);

    private final InscricaoPushService inscricaoPushService;
    private final EnviadorPush enviadorPush;
    private final ObjectMapper objectMapper;

    public NotificadorBdoNovo(InscricaoPushService inscricaoPushService, EnviadorPush enviadorPush, ObjectMapper objectMapper) {
        this.inscricaoPushService = inscricaoPushService;
        this.enviadorPush = enviadorPush;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoReceberBdoPorEmail(BdoRecebidoPorEmail evento) {
        if (!enviadorPush.habilitado()) {
            return;
        }
        List<InscricaoPush> inscricoes = inscricaoPushService.listarDosMotoristasAtivos();
        String conteudo = conteudo(evento);
        int enviados = 0;
        for (InscricaoPush inscricao : inscricoes) {
            try {
                int status = enviadorPush.enviar(inscricao, conteudo);
                if (status == 404 || status == 410) {
                    inscricaoPushService.removerExpirada(inscricao.getId());
                } else if (status >= 400) {
                    log.warn("Servico de push recusou o aviso do BDO {} (HTTP {}) para a inscricao {}.", evento.bdoId(), status, inscricao.getId());
                } else {
                    enviados++;
                }
            } catch (Exception e) {
                log.warn("Falha ao enviar o aviso do BDO {} para a inscricao {} - seguindo com as demais.", evento.bdoId(), inscricao.getId(), e);
            }
        }
        log.info("Aviso de BDO novo {} enviado a {} de {} aparelho(s).", evento.bdoId(), enviados, inscricoes.size());
    }

    String conteudo(BdoRecebidoPorEmail evento) {
        Map<String, String> notificacao = new LinkedHashMap<>();
        notificacao.put("titulo", "Nova bagagem para retirar no aeroporto");
        // Com acentos, ao contrario das mensagens da API: este texto aparece direto na notificacao do celular.
        notificacao.put("corpo", evento.numero() == null
                ? "Um BDO novo chegou por e-mail e está disponível para assumir."
                : "BDO " + evento.numero() + " chegou por e-mail e está disponível para assumir.");
        notificacao.put("url", "/motorista");
        notificacao.put("bdoId", evento.bdoId().toString());
        try {
            return objectMapper.writeValueAsString(notificacao);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o aviso de BDO novo.", e);
        }
    }
}
