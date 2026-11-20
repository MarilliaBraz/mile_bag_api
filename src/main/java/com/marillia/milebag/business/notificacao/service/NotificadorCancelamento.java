package com.marillia.milebag.business.notificacao.service;

import java.util.LinkedHashMap;
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
import com.marillia.milebag.business.cancelamento.model.BdoCancelado;

/**
 * Avisa o motorista que estava com a entrega de que a empresa cancelou o BDO (V47) - nos aparelhos dele, com o motivo.
 *
 * <p>Depois do commit (so de um cancelamento registrado) e numa transacao nova, para a leitura das inscricoes passar
 * pela RLS do tenant da requisicao, como em {@link NotificadorBdoNovo}. Nunca lanca. Com o app aberto, o service worker
 * repassa o aviso a tela pelo {@code tipo} (public/sw-push.js).</p>
 */
@Component
public class NotificadorCancelamento {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCancelamento.class);

    static final String TIPO = "bdo-cancelado";

    private final InscricaoPushService inscricaoPushService;
    private final DisparadorPush disparadorPush;
    private final ObjectMapper objectMapper;

    public NotificadorCancelamento(InscricaoPushService inscricaoPushService, DisparadorPush disparadorPush, ObjectMapper objectMapper) {
        this.inscricaoPushService = inscricaoPushService;
        this.disparadorPush = disparadorPush;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCancelarBdo(BdoCancelado evento) {
        if (!disparadorPush.habilitado()) {
            return;
        }
        try {
            disparadorPush.disparar(
                    inscricaoPushService.listarDoUsuario(evento.motoristaId()), conteudo(evento), "de BDO cancelado " + evento.bdoId());
        } catch (RuntimeException e) {
            // O cancelamento ja esta gravado: o motorista ainda ve a mensagem ao abrir ou agir na entrega.
            log.error("Falha ao avisar o motorista {} do cancelamento do BDO {} - cancelamento mantido.",
                    evento.motoristaId(), evento.bdoId(), e);
        }
    }

    private static String semPontoFinal(String texto) {
        return texto != null && texto.endsWith(".") ? texto.substring(0, texto.length() - 1) : texto;
    }

    String conteudo(BdoCancelado evento) {
        // Com acentos, ao contrario das mensagens da API: este texto aparece direto na notificacao do celular.
        String bdo = evento.bdoNumero() == null ? "uma entrega sua" : "a entrega do BDO " + evento.bdoNumero();
        Map<String, String> notificacao = new LinkedHashMap<>();
        notificacao.put("tipo", TIPO);
        notificacao.put("titulo", "Entrega cancelada pela empresa");
        notificacao.put("corpo", "A empresa cancelou " + bdo + ". Motivo: " + semPontoFinal(evento.motivo()) + ".");
        notificacao.put("url", "/motorista");
        notificacao.put("bdoId", evento.bdoId().toString());
        try {
            return objectMapper.writeValueAsString(notificacao);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o aviso de BDO cancelado.", e);
        }
    }
}
