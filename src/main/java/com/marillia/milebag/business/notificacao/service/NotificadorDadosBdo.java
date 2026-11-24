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
import com.marillia.milebag.business.dadosbdo.model.DadosBdoAlterados;

/**
 * Avisa o motorista que esta com a entrega de que a empresa corrigiu os dados do passageiro (V59) - nos aparelhos
 * dele. Depois do commit e numa transacao nova, como em {@link NotificadorCancelamento}. Nunca lanca: a correcao ja
 * esta gravada e o motorista ve os dados novos ao abrir a entrega.
 */
@Component
public class NotificadorDadosBdo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorDadosBdo.class);

    static final String TIPO = "bdo-dados-alterados";

    private final InscricaoPushService inscricaoPushService;
    private final DisparadorPush disparadorPush;
    private final ObjectMapper objectMapper;

    public NotificadorDadosBdo(InscricaoPushService inscricaoPushService, DisparadorPush disparadorPush, ObjectMapper objectMapper) {
        this.inscricaoPushService = inscricaoPushService;
        this.disparadorPush = disparadorPush;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAlterarDados(DadosBdoAlterados evento) {
        if (!disparadorPush.habilitado()) {
            return;
        }
        try {
            disparadorPush.disparar(inscricaoPushService.listarDoUsuario(evento.motoristaId()), conteudo(evento),
                    "de dados do BDO alterados " + evento.bdoId());
        } catch (RuntimeException e) {
            log.error("Falha ao avisar o motorista {} da correcao dos dados do BDO {} - correcao mantida.",
                    evento.motoristaId(), evento.bdoId(), e);
        }
    }

    String conteudo(DadosBdoAlterados evento) {
        // Com acentos, ao contrario das mensagens da API: este texto aparece direto na notificacao do celular.
        String bdo = evento.bdoNumero() == null ? "de uma entrega sua" : "do BDO " + evento.bdoNumero();
        String corpo = evento.enderecoMudou()
                ? "A empresa mudou o endereço de entrega " + bdo + ". Confira o endereço novo antes de seguir."
                : "A empresa corrigiu os dados do passageiro " + bdo + ". Confira na entrega.";
        Map<String, String> notificacao = new LinkedHashMap<>();
        notificacao.put("tipo", TIPO);
        notificacao.put("titulo", "Dados da entrega alterados pela empresa");
        notificacao.put("corpo", corpo);
        notificacao.put("url", "/motorista");
        notificacao.put("bdoId", evento.bdoId().toString());
        try {
            return objectMapper.writeValueAsString(notificacao);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o aviso de dados do BDO alterados.", e);
        }
    }
}
