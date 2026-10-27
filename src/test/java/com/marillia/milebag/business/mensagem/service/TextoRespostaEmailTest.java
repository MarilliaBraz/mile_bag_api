package com.marillia.milebag.business.mensagem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

class TextoRespostaEmailTest {

    private static final String PREFIXO = MensagemService.PREFIXO_ASSUNTO_CONVERSA;

    @Test
    void textoSimples() throws Exception {
        MimeMessage mensagem = mensagem("Oi");
        mensagem.setText("Estou em casa a tarde toda.\r\n");

        assertThat(TextoRespostaEmail.extrair(mensagem, PREFIXO)).isEqualTo("Estou em casa a tarde toda.");
    }

    @Test
    void respostaDoGmailSemACitacaoDaMensagemOriginal() throws Exception {
        MimeMessage mensagem = mensagem("Re: " + PREFIXO + "GYNLA12342");
        mensagem.setContent(alternativa("""
                Pode deixar na portaria.\r
                \r
                Em seg., 26 de out. de 2026 às 08:44, Milebag <entrega-d130c5c1-1702-455c-82cc-1198e890b6fb@milebag.com.br>\r
                escreveu:\r
                \r
                > Ola! Aqui e o motorista do Milebag.\r
                >\r
                """, "<div>Pode deixar na portaria.</div>"));
        mensagem.saveChanges();

        assertThat(TextoRespostaEmail.extrair(mensagem, PREFIXO)).isEqualTo("Pode deixar na portaria.");
    }

    @Test
    void cabecalhoDeCitacaoNumaLinhaSoESeparadorDoOutlook() {
        assertThat(TextoRespostaEmail.semCitacao("Obrigado!\nOn Mon, Oct 26, 2026 at 8:44 AM Milebag wrote:\n> Ola"))
                .isEqualTo("Obrigado!");
        assertThat(TextoRespostaEmail.semCitacao("Combinado.\n\n-----Mensagem original-----\nDe: Milebag"))
                .isEqualTo("Combinado.");
        assertThat(TextoRespostaEmail.semCitacao("Combinado.\n________________________________\nDe: Milebag"))
                .isEqualTo("Combinado.");
    }

    @Test
    void semAssinatura() {
        assertThat(TextoRespostaEmail.semCitacao("Chego as 18h.\n-- \nMaria Souza\n(62) 99999-0000")).isEqualTo("Chego as 18h.");
    }

    @Test
    void respostaEscritaAbaixoDaCitacao() {
        assertThat(TextoRespostaEmail.semCitacao("Em seg., 26 de out. de 2026, Milebag escreveu:\n> Ola!\n\nPode vir agora.\n"))
                .isEqualTo("Pode vir agora.");
    }

    @Test
    void textoDentroDeMultipartAninhadoComAnexo() throws Exception {
        MimeMultipart misto = new MimeMultipart("mixed");
        MimeBodyPart corpo = new MimeBodyPart();
        corpo.setContent(alternativa("Segue a foto do comprovante.", "<p>Segue a foto do comprovante.</p>"));
        misto.addBodyPart(corpo);
        MimeBodyPart anexo = new MimeBodyPart();
        anexo.setText("conteudo do anexo");
        anexo.setFileName("comprovante.txt");
        misto.addBodyPart(anexo);
        MimeMessage mensagem = mensagem("Comprovante");
        mensagem.setContent(misto);
        mensagem.saveChanges();

        assertThat(TextoRespostaEmail.extrair(mensagem, PREFIXO)).isEqualTo("Segue a foto do comprovante.");
    }

    @Test
    void corpoVazioUsaOAssuntoSemORe() throws Exception {
        MimeMessage mensagem = mensagem("RE: Ja estou no endereco");
        mensagem.setContent(alternativa("\r\n\r\n", "<div><img src=\"assinatura.png\"></div>"));
        mensagem.saveChanges();

        assertThat(TextoRespostaEmail.extrair(mensagem, PREFIXO)).isEqualTo("Ja estou no endereco");
    }

    @Test
    void corpoVazioComOAssuntoDaPropriaConversaFicaVazio() throws Exception {
        MimeMessage mensagem = mensagem("Re: " + PREFIXO + "GYNLA12342");
        mensagem.setText("\r\n");

        assertThat(TextoRespostaEmail.extrair(mensagem, PREFIXO)).isEmpty();
    }

    @Test
    void semTextoNemAssuntoFicaVazio() throws Exception {
        MimeMessage mensagem = mensagem(null);
        mensagem.setContent("<p></p>", "text/html; charset=UTF-8");
        mensagem.saveChanges();

        assertThat(TextoRespostaEmail.extrair(mensagem, PREFIXO)).isEmpty();
    }

    private static MimeMessage mensagem(String assunto) throws Exception {
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        mensagem.setSubject(assunto, "UTF-8");
        return mensagem;
    }

    private static MimeMultipart alternativa(String texto, String html) throws Exception {
        MimeMultipart alternativa = new MimeMultipart("alternative");
        MimeBodyPart parteTexto = new MimeBodyPart();
        parteTexto.setText(texto, "UTF-8");
        alternativa.addBodyPart(parteTexto);
        MimeBodyPart parteHtml = new MimeBodyPart();
        parteHtml.setContent(html, "text/html; charset=UTF-8");
        alternativa.addBodyPart(parteHtml);
        return alternativa;
    }
}
