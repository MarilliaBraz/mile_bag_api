package com.marillia.milebag.core.mail;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marillia.milebag.core.config.SmtpProperties;

import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

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
    public void enviar(ContaEnvioEmail conta, String destinatario, String assunto, String corpo, String replyTo, AnexoEmail anexo)
            throws MessagingException {
        if (!smtpProperties.habilitado()) {
            log.warn("Envio de e-mail desabilitado (milebag.smtp.habilitado=false) - nenhum e-mail enviado para {}.", destinatario);
            return;
        }

        Properties propriedadesSessao = new Properties();
        propriedadesSessao.put("mail.smtp.auth", "true");
        propriedadesSessao.put("mail.smtp.starttls.enable", "true");
        propriedadesSessao.put("mail.smtp.host", smtpProperties.host());
        propriedadesSessao.put("mail.smtp.port", String.valueOf(smtpProperties.port()));

        String usuario = conta == null ? smtpProperties.usuario() : conta.usuario();
        String senha = conta == null ? smtpProperties.senha() : conta.senha();
        String remetente = conta == null ? smtpProperties.remetente() : conta.remetente();
        Session sessao = Session.getInstance(propriedadesSessao, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(usuario, senha);
            }
        });

        Message mensagem = new MimeMessage(sessao);
        mensagem.setFrom(new InternetAddress(remetente));
        mensagem.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        if (replyTo != null && !replyTo.isBlank()) {
            mensagem.setReplyTo(InternetAddress.parse(replyTo));
        }
        mensagem.setSubject(assunto);
        if (anexo == null) {
            mensagem.setText(corpo);
        } else {
            MimeBodyPart texto = new MimeBodyPart();
            texto.setText(corpo, "UTF-8");
            MimeBodyPart arquivo = new MimeBodyPart();
            arquivo.setDataHandler(new DataHandler(new ByteArrayDataSource(anexo.conteudo(), anexo.tipoConteudo())));
            arquivo.setFileName(anexo.nomeArquivo());
            MimeMultipart partes = new MimeMultipart();
            partes.addBodyPart(texto);
            partes.addBodyPart(arquivo);
            mensagem.setContent(partes);
        }
        Transport.send(mensagem);
    }
}
