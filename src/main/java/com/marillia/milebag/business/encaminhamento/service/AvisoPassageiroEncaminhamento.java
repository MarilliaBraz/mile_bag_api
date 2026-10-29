package com.marillia.milebag.business.encaminhamento.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.marillia.milebag.business.encaminhamento.model.BagagemEncaminhada;
import com.marillia.milebag.core.mail.EmailSender;

/**
 * Avisa o passageiro de que a bagagem foi encontrada e encaminhada para entrega (HU37, RF30). Depois do commit: so avisa
 * de um encaminhamento que ficou registrado. Nunca lanca - a falha do e-mail nao desfaz o encaminhamento. O link
 * "Acompanhe sua bagagem" (HU31) chega pela mensageria quando o motorista iniciar a viagem.
 */
@Component
public class AvisoPassageiroEncaminhamento {

    private static final Logger log = LoggerFactory.getLogger(AvisoPassageiroEncaminhamento.class);

    private final EmailSender emailSender;

    public AvisoPassageiroEncaminhamento(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEncaminhar(BagagemEncaminhada evento) {
        try {
            emailSender.enviar(evento.email(), assunto(evento), corpo(evento));
            log.info("Passageiro avisado do encaminhamento {}.", evento.encaminhamentoId());
        } catch (Exception e) {
            log.error("Falha ao avisar o passageiro do encaminhamento {} - encaminhamento mantido.", evento.encaminhamentoId(), e);
        }
    }

    // Com acentos, ao contrario das mensagens da API: o texto vai direto para o passageiro.
    static String assunto(BagagemEncaminhada evento) {
        return "Sua bagagem foi encontrada - " + evento.companhia();
    }

    static String corpo(BagagemEncaminhada evento) {
        return "Olá, " + evento.passageiroNome() + ".\n\n"
                + "A " + evento.companhia() + " encontrou a sua bagagem (" + evento.identificacao() + ") e a encaminhou para entrega pela "
                + evento.empresa() + ".\n\n"
                + "Quando o motorista sair para o endereço de entrega, você receberá um link para acompanhar a bagagem em tempo real.\n\n"
                + "Esta é uma mensagem automática do Milebag; não é preciso responder.";
    }
}
