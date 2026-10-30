package com.marillia.milebag.business.notificacao.service;

import java.security.GeneralSecurityException;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marillia.milebag.business.notificacao.model.InscricaoPush;
import com.marillia.milebag.core.config.PushProperties;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;

/**
 * Envio Web Push com VAPID (HU32), sobre {@code nl.martijndwars:web-push}. Sem as chaves do ambiente (ou com chaves
 * invalidas) fica desligado: o aviso de BDO novo continua valendo com o app aberto, por consulta periodica.
 */
@Component
public class EnviadorPush {

    private static final Logger log = LoggerFactory.getLogger(EnviadorPush.class);

    private final PushService pushService;
    private final String chavePublica;

    public EnviadorPush(PushProperties pushProperties) {
        this.pushService = criar(pushProperties);
        this.chavePublica = pushProperties.chavePublica();
    }

    public boolean habilitado() {
        return pushService != null;
    }

    /** A mesma chave publica VAPID do ambiente (base64url), usada pelo navegador para se inscrever. */
    public String chavePublica() {
        return habilitado() ? chavePublica : null;
    }

    /** Devolve o status HTTP do servico de push do navegador (201 = aceito; 404/410 = inscricao inexistente). */
    public int enviar(InscricaoPush inscricao, String conteudo) throws Exception {
        Subscription destino = new Subscription(inscricao.getEndpoint(), new Subscription.Keys(inscricao.getChaveP256dh(), inscricao.getChaveAuth()));
        return pushService.send(new Notification(destino, conteudo), Encoding.AES128GCM).getStatusLine().getStatusCode();
    }

    private static PushService criar(PushProperties propriedades) {
        if (!propriedades.habilitado()) {
            log.info("Web Push desligado: sem chaves VAPID (milebag.push) - aviso de BDO novo so com o app aberto.");
            return null;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            return new PushService(propriedades.chavePublica(), propriedades.chavePrivada(), propriedades.assunto());
        } catch (GeneralSecurityException e) {
            log.error("Chaves VAPID invalidas (milebag.push) - Web Push desligado.", e);
            return null;
        }
    }
}
