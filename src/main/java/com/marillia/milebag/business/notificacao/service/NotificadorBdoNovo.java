package com.marillia.milebag.business.notificacao.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.bdo.model.BdoEncaminhadoPorCompanhia;
import com.marillia.milebag.business.bdo.model.BdoIncluidoPelaEmpresa;
import com.marillia.milebag.business.bdo.model.BdoMudouDeBase;
import com.marillia.milebag.business.bdo.model.BdoRecebidoPorEmail;
import com.marillia.milebag.business.notificacao.model.InscricaoPush;
import com.marillia.milebag.core.config.security.TenantContext;

/**
 * Avisa os aparelhos inscritos dos motoristas ativos do tenant quando um BDO novo fica disponivel: por e-mail (HU32,
 * RF25), pela companhia aerea (HU37) ou incluido pela empresa (V49).
 *
 * <p>Depois do commit: so avisa de um BDO que de fato ficou registrado. Numa transacao nova, porque a do registro ja
 * terminou e a leitura das inscricoes precisa de transacao para a RLS valer (TenantAspect); o tenant continua no
 * {@code TenantContext} definido por {@code BdoEmailListener}. Nunca lanca: falha num aparelho nao impede os outros
 * nem o registro do BDO.</p>
 */
@Component
public class NotificadorBdoNovo {

    private final InscricaoPushService inscricaoPushService;
    private final DisparadorPush disparadorPush;
    private final ObjectMapper objectMapper;

    public NotificadorBdoNovo(InscricaoPushService inscricaoPushService, DisparadorPush disparadorPush, ObjectMapper objectMapper) {
        this.inscricaoPushService = inscricaoPushService;
        this.disparadorPush = disparadorPush;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoReceberBdoPorEmail(BdoRecebidoPorEmail evento) {
        avisar(evento.bdoId(), conteudo(evento));
    }

    /** V49: a empresa incluiu o BDO pelo painel - o tenant e o da requisicao do administrador, ainda no contexto. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoIncluirBdoPelaEmpresa(BdoIncluidoPelaEmpresa evento) {
        avisar(evento.bdoId(), conteudo(evento));
    }

    /**
     * HU37: o BDO nasceu numa requisicao da companhia aerea, entao o tenant da empresa vem no evento e vale so durante
     * o aviso (a transacao nova aplica o tenant a cada consulta, via TenantAspect).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoReceberBdoDaCompanhia(BdoEncaminhadoPorCompanhia evento) {
        UUID anterior = TenantContext.get();
        TenantContext.set(evento.empresaId());
        try {
            avisar(evento.bdoId(), conteudo(evento));
        } finally {
            if (anterior == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(anterior);
            }
        }
    }

    private void avisar(UUID bdoId, String conteudo) {
        if (!disparadorPush.habilitado()) {
            return;
        }
        // V52: so os motoristas da base do BDO.
        disparadorPush.disparar(inscricaoPushService.listarDosMotoristasDoBdo(bdoId), conteudo, "de BDO novo " + bdoId);
    }

    String conteudo(BdoRecebidoPorEmail evento) {
        // Com acentos, ao contrario das mensagens da API: este texto aparece direto na notificacao do celular.
        return json(evento.bdoId(), evento.numero() == null
                ? "Um BDO novo chegou por e-mail e está disponível para assumir."
                : "BDO " + evento.numero() + " chegou por e-mail e está disponível para assumir.");
    }

    /** V52: a empresa passou o BDO para outra base - avisa os motoristas da base nova. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoMudarDeBase(BdoMudouDeBase evento) {
        avisar(evento.bdoId(), json(evento.bdoId(), evento.numero() == null
                ? "Um BDO passou para a sua base e está disponível para assumir."
                : "O BDO " + evento.numero() + " passou para a sua base e está disponível para assumir."));
    }

    String conteudo(BdoIncluidoPelaEmpresa evento) {
        return json(evento.bdoId(), evento.numero() == null
                ? "A empresa incluiu um BDO novo, disponível para assumir."
                : "A empresa incluiu o BDO " + evento.numero() + ", disponível para assumir.");
    }

    String conteudo(BdoEncaminhadoPorCompanhia evento) {
        String corpo = evento.numero() == null
                ? "A " + evento.companhia() + " encaminhou uma bagagem encontrada, disponível para assumir."
                : "A " + evento.companhia() + " encaminhou a bagagem do processo " + evento.numero() + ", disponível para assumir.";
        return json(evento.bdoId(), corpo);
    }

    private String json(UUID bdoId, String corpo) {
        Map<String, String> notificacao = new LinkedHashMap<>();
        notificacao.put("titulo", "Nova bagagem para retirar no aeroporto");
        notificacao.put("corpo", corpo);
        notificacao.put("url", "/motorista");
        notificacao.put("bdoId", bdoId.toString());
        try {
            return objectMapper.writeValueAsString(notificacao);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o aviso de BDO novo.", e);
        }
    }
}
