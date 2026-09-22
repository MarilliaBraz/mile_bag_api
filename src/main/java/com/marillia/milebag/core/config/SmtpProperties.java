package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envio de e-mail transacional (convite de administrador - HU15). Mesmo
 * padrao de {@link ImapProperties}: {@code habilitado} comeca falso ate a
 * infraestrutura de e-mail self-hosted (HU18) estar de fato provisionada -
 * {@code EmailSenderSmtp} so tenta conectar quando estiver true.
 */
@ConfigurationProperties(prefix = "milebag.smtp")
public record SmtpProperties(boolean habilitado, String host, int port, String usuario, String senha, String remetente) {
}
