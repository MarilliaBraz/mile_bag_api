package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.marillia.milebag.core.mail.ContaEnvioEmail;

/**
 * Canal de e-mail da conversa com o passageiro (HU18) - caixa IMAP propria
 * (distinta de {@link ImapProperties}, usada pela captura de BDO por
 * e-mail de HU02): as duas nao podem compartilhar a mesma caixa, senao um
 * listener consome como "lido" um e-mail que o outro ainda precisa
 * processar (ver javadoc de {@code EmailInboundListener}).
 *
 * <p>{@code smtpUsuario} vazio: a conversa sai pela conta padrao de {@code milebag.smtp}. {@code prefixoEndereco} vai
 * antes de {@code entrega-{id}} no {@code Reply-To} - ex. {@code mensagem.motorista+} quando o provedor entrega o
 * subendereco na caixa e o dominio nao tem conta "pega-tudo". {@code imapPasta}: pasta lida (no Gmail, o nome do
 * marcador) - outra que nao a INBOX quando a caixa e compartilhada, ja que toda mensagem lida e marcada como vista.</p>
 */
@ConfigurationProperties(prefix = "milebag.mensageria")
public record MensageriaProperties(
        boolean imapHabilitado,
        String imapHost,
        int imapPort,
        String imapUsuario,
        String imapSenha,
        long imapIntervaloConsultaMs,
        String dominioEmail,
        String prefixoEndereco,
        String smtpUsuario,
        String smtpSenha,
        String smtpRemetente,
        String imapPasta) {

    /** Pasta IMAP lida pelo {@code EmailInboundListener}; vazia = INBOX. */
    public String pastaLeitura() {
        return imapPasta == null || imapPasta.isBlank() ? "INBOX" : imapPasta;
    }

    /** Conta propria de envio da conversa, ou {@code null} para usar a padrao. */
    public ContaEnvioEmail contaEnvio() {
        if (smtpUsuario == null || smtpUsuario.isBlank()) {
            return null;
        }
        return new ContaEnvioEmail(smtpUsuario, smtpSenha, smtpRemetente == null || smtpRemetente.isBlank() ? smtpUsuario : smtpRemetente);
    }
}
