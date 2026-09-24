package com.marillia.milebag.business.mensagem.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.core.config.MensageriaProperties;
import com.marillia.milebag.core.config.SmtpProperties;
import com.marillia.milebag.core.config.TelegramProperties;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

/**
 * Regressao encontrada na validacao com o servidor real: com o SMTP
 * desabilitado, {@code EmailSender} nao lanca (so avisa), e o canal
 * reportava sucesso - a Mensagem ficava ENVIADA sem nenhum e-mail ter saido.
 */
@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

    @Mock
    private EmailSender emailSender;

    private EmailChannel canal(boolean smtpHabilitado) {
        return new EmailChannel(
                emailSender,
                new MensageriaProperties(false, "", 993, "", "", 60000, "mensagens.milebag.local"),
                new TelegramProperties(false, "", "milebag_bot", "", "https://api.telegram.org"),
                new SmtpProperties(smtpHabilitado, "smtp", 587, "u", "s", "no-reply@milebag.local"));
    }

    @Test
    void naoReportaSucessoQuandoSmtpDesabilitado() {
        boolean enviado = canal(false).enviar(UUID.randomUUID(), "a@b.com", "assunto", "oi", false);

        assertThat(enviado).isFalse();
        verifyNoInteractions(emailSender);
    }

    @Test
    void enviaComReplyToUnicoPorEntregaQuandoHabilitado() throws Exception {
        UUID entregaId = UUID.randomUUID();

        boolean enviado = canal(true).enviar(entregaId, "a@b.com", "assunto", "oi", false);

        assertThat(enviado).isTrue();
        verify(emailSender).enviar("a@b.com", "assunto", "oi", "entrega-" + entregaId + "@mensagens.milebag.local");
    }

    @Test
    void incluiLinkDeConviteDoTelegramQuandoPedido() throws Exception {
        UUID entregaId = UUID.randomUUID();

        canal(true).enviar(entregaId, "a@b.com", "assunto", "oi", true);

        verify(emailSender).enviar(
                eq("a@b.com"), eq("assunto"), org.mockito.ArgumentMatchers.contains("https://t.me/milebag_bot?start=" + entregaId), any());
    }

    @Test
    void reportaFalhaQuandoOEnvioLancaExcecao() throws Exception {
        doThrow(new MessagingException("smtp fora")).when(emailSender).enviar(any(), any(), any(), any());

        assertThat(canal(true).enviar(UUID.randomUUID(), "a@b.com", "assunto", "oi", false)).isFalse();
        verify(emailSender, never()).enviar("a@b.com", "assunto", "oi");
    }
}
