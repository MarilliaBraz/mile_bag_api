package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexao com a caixa de entrada compartilhada de recebimento automatico de
 * BDO (RF21) - servidor self-hosted (Postfix/Dovecot) decidido em HU18,
 * reaproveitado por esta HU (research.md desta HU, secao "Reaproveitar a
 * infraestrutura de e-mail de HU18"). {@code habilitado} comeca falso: o
 * {@code BdoEmailListener} so tenta conectar quando essa infraestrutura
 * estiver de fato provisionada.
 */
@ConfigurationProperties(prefix = "milebag.imap")
public record ImapProperties(
        boolean habilitado, String host, int port, String usuario, String senha, long intervaloConsultaMs) {
}
