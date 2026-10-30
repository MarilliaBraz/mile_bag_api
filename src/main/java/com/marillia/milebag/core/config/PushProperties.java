package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chaves VAPID do Web Push (HU32) - um par por ambiente, gerado na 1a implantacao (mile_bag_infra,
 * {@code implantar.sh}). Sem as duas chaves o push fica desligado e o aviso de BDO novo vale so com o app aberto.
 * {@code assunto} identifica o remetente para os servicos de push (um {@code mailto:} ou a URL publica).
 */
@ConfigurationProperties(prefix = "milebag.push")
public record PushProperties(String chavePublica, String chavePrivada, String assunto) {

    public boolean habilitado() {
        return chavePublica != null && !chavePublica.isBlank() && chavePrivada != null && !chavePrivada.isBlank();
    }
}
