package com.marillia.milebag.business.demonstracao.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.demonstracao.model.PedidoDemonstracaoRequest;
import com.marillia.milebag.business.demonstracao.model.PerfilCliente;
import com.marillia.milebag.core.config.DemonstracaoProperties;
import com.marillia.milebag.core.config.SmtpProperties;
import com.marillia.milebag.core.exception.EnvioIndisponivelException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

@ExtendWith(MockitoExtension.class)
class DemonstracaoServiceTest {

    private static final String IP = "203.0.113.7";
    private static final SmtpProperties SMTP_LIGADO = new SmtpProperties(true, "smtp.exemplo.com", 587, "u", "s", "noreply@milebag.com.br");
    private static final SmtpProperties SMTP_DESLIGADO = new SmtpProperties(false, "", 587, "", "", "");

    @Mock
    private EmailSender emailSender;
    @Mock
    private LimitadorPedidosDemonstracao limitador;

    private DemonstracaoService service;

    @BeforeEach
    void setUp() {
        service = criar(SMTP_LIGADO);
    }

    private DemonstracaoService criar(SmtpProperties smtp) {
        return new DemonstracaoService(emailSender, smtp, new DemonstracaoProperties("marillia.dev@gmail.com"), limitador, "America/Sao_Paulo");
    }

    private static PedidoDemonstracaoRequest pedido(String email, String telefone, String mensagem, Boolean consentimento, String site, Long ms) {
        return new PedidoDemonstracaoRequest("  Ana Lima ", "Transportes Exemplo", PerfilCliente.EMPRESA_ENTREGA, email, telefone, mensagem, consentimento, site,
                ms);
    }

    private static PedidoDemonstracaoRequest valido() {
        return pedido("ana@exemplo.com", "+55 62 99999-0000", "Atendemos GYN.", true, "", 42_000L);
    }

    @Test
    void pedidoValidoEnviaParaODestinoComReplyToDoInteressado() throws MessagingException {
        service.pedir(valido(), IP);

        verify(limitador).registrar(IP);
        verify(emailSender).enviar(eq("marillia.dev@gmail.com"), contains("Transportes Exemplo (Empresa de entrega)"), contains("Ana Lima"),
                eq("ana@exemplo.com"));
    }

    @Test
    void quebraDeLinhaNaEmpresaNaoChegaAoAssunto() throws MessagingException {
        PedidoDemonstracaoRequest comQuebra = new PedidoDemonstracaoRequest("Ana\nLima", "Exemplo\r\nBcc: alvo@exemplo.com", PerfilCliente.COMPANHIA_AEREA,
                "ana@exemplo.com", null, null, true, "", 42_000L);

        service.pedir(comQuebra, IP);

        verify(emailSender).enviar(eq("marillia.dev@gmail.com"), eq("Pedido de demonstração - Exemplo Bcc: alvo@exemplo.com (Companhia aérea)"),
                contains("Nome: Ana Lima\n"), eq("ana@exemplo.com"));
    }

    @Test
    void campoIscaPreenchidoNaoEnviaENaoFalha() throws MessagingException {
        service.pedir(pedido("ana@exemplo.com", null, null, true, "http://spam", 42_000L), IP);

        verify(emailSender, never()).enviar(anyString(), anyString(), anyString(), anyString());
        verify(limitador, never()).registrar(anyString());
    }

    @Test
    void preenchimentoRapidoNaoEnvia() throws MessagingException {
        service.pedir(pedido("ana@exemplo.com", null, null, true, "", 800L), IP);

        verify(emailSender, never()).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void semConsentimentoResponde400() {
        assertThatThrownBy(() -> service.pedir(pedido("ana@exemplo.com", null, null, false, "", 42_000L), IP))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void emailInvalidoResponde400() {
        assertThatThrownBy(() -> service.pedir(pedido("ana@", null, null, true, "", 42_000L), IP)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void telefoneComLetrasResponde400() {
        assertThatThrownBy(() -> service.pedir(pedido("ana@exemplo.com", "ligue amanha", null, true, "", 42_000L), IP))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void mensagemAcimaDe2000Responde400() {
        assertThatThrownBy(() -> service.pedir(pedido("ana@exemplo.com", null, "x".repeat(2001), true, "", 42_000L), IP))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void smtpDesligadoResponde503() throws MessagingException {
        DemonstracaoService semSmtp = criar(SMTP_DESLIGADO);

        assertThatThrownBy(() -> semSmtp.pedir(valido(), IP)).isInstanceOf(EnvioIndisponivelException.class);
        verify(emailSender, never()).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void falhaNoEnvioResponde503() throws MessagingException {
        doThrow(new MessagingException("conexao recusada")).when(emailSender).enviar(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.pedir(valido(), IP)).isInstanceOf(EnvioIndisponivelException.class);
    }
}
