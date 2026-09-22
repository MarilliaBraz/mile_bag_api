package com.marillia.milebag.core.mail;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marillia.milebag.core.config.SmtpProperties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Envio via SMTP usando {@code jakarta.mail} - mesma biblioteca ja usada
 * por {@code BdoEmailListener} (HU02) para IMAP, evitando adicionar
 * {@code spring-boot-starter-mail} so para o lado de envio.
 */
@Component
public class EmailSenderSmtp implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderSmtp.class);

    private final SmtpProperties smtpProperties;

    public EmailSenderSmtp(SmtpProperties smtpProperties) {
        this.smtpProperties = smtpProperties;
    }

    @Override
    public void enviar(String destinatario, String assunto, String corpo) throws MessagingException {
        if (!smtpProperties.habilitado()) {
            log.warn("Envio de e-mail desabilitado (milebag.smtp.habilitado=false) - nenhum e-mail enviado para {}.", destinatario);
            return;
        }

        Properties propriedadesSessao = new Properties();
        propriedadesSessao.put("mail.smtp.auth", "true");
        propriedadesSessao.put("mail.smtp.starttls.enable", "true");
        propriedadesSessao.put("mail.smtp.host", smtpProperties.host());
        propriedadesSessao.put("mail.smtp.port", String.valueOf(smtpProperties.port()));

        Session sessao = Session.getInstance(propriedadesSessao, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpProperties.usuario(), smtpProperties.senha());
            }
        });

        Message mensagem = new MimeMessage(sessao);
        mensagem.setFrom(new InternetAddress(smtpProperties.remetente()));
        mensagem.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        mensagem.setSubject(assunto);
        mensagem.setText(corpo);
        Transport.send(mensagem);
    }
}
