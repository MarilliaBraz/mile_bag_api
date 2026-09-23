package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Canal de e-mail da conversa com o passageiro (HU18) - caixa IMAP propria
 * (distinta de {@link ImapProperties}, usada pela captura de BDO por
 * e-mail de HU02): as duas nao podem compartilhar a mesma caixa, senao um
 * listener consome como "lido" um e-mail que o outro ainda precisa
 * processar (ver javadoc de {@code EmailInboundListener}).
 */
@ConfigurationProperties(prefix = "milebag.mensageria")
public record MensageriaProperties(
        boolean imapHabilitado,
        String imapHost,
        int imapPort,
        String imapUsuario,
        String imapSenha,
        long imapIntervaloConsultaMs,
        String dominioEmail) {
}
