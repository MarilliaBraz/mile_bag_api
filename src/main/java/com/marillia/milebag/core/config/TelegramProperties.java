package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bot da plataforma na API do Telegram (HU18) - unico para todos os
 * tenants (FR-010). Mesmo padrao de {@code habilitado} de {@link
 * SmtpProperties}/{@link ImapProperties}: {@code TelegramClient} so tenta
 * chamar a Bot API quando o token estiver de fato configurado.
 */
@ConfigurationProperties(prefix = "milebag.telegram")
public record TelegramProperties(boolean habilitado, String botToken, String botUsername, String webhookSecret) {
}
