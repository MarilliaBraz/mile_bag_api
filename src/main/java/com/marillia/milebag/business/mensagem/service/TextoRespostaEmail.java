package com.marillia.milebag.business.mensagem.service;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;

/**
 * Texto que o passageiro escreveu num e-mail de resposta da conversa (HU18),
 * lido por {@link EmailInboundListener}. O corpo cru nao serve direto: o
 * cliente de e-mail anexa a mensagem original citada e a assinatura, e um
 * e-mail com anexo traz o text/plain dentro de multipart aninhado
 * (multipart/mixed > multipart/alternative).
 *
 * <p>Sem texto no corpo (so o assunto preenchido, ou so uma assinatura em
 * imagem), vale o assunto - salvo quando ele e apenas o "Re:" do assunto
 * da propria conversa, que nao diz nada. Sem nenhum dos dois, devolve
 * vazio e o e-mail e ignorado.</p>
 */
final class TextoRespostaEmail {

    private static final Pattern PREFIXO_RESPOSTA = Pattern.compile("^\\s*((re|res|fw|fwd|enc|rv)\\s*:\\s*)+", Pattern.CASE_INSENSITIVE);
    // Inicio do trecho citado: "> ...", o cabecalho "Em <data>, <nome> escreveu:" (Gmail, Apple Mail; em
    // ingles "On ... wrote:") e os separadores do Outlook.
    private static final Pattern LINHA_CITADA = Pattern.compile("^>");
    private static final Pattern CABECALHO_CITACAO = Pattern.compile("^(em|on)\\s.*(escreveu|wrote)\\s*:\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARADOR_OUTLOOK = Pattern.compile("^(-{2,}\\s*(original message|mensagem original)\\s*-{2,}|_{10,})\\s*$", Pattern.CASE_INSENSITIVE);
    // Delimitador de assinatura (RFC 3676): "-- " sozinho na linha.
    private static final String DELIMITADOR_ASSINATURA = "-- ";

    private TextoRespostaEmail() {
    }

    static String extrair(Message mensagem, String prefixoAssuntoConversa) throws MessagingException, IOException {
        String texto = semCitacao(textoSimples(mensagem));
        if (!texto.isEmpty()) {
            return texto;
        }
        String assunto = mensagem.getSubject() == null ? "" : PREFIXO_RESPOSTA.matcher(mensagem.getSubject()).replaceFirst("").strip();
        return assunto.startsWith(prefixoAssuntoConversa) ? "" : assunto;
    }

    private static String textoSimples(Part parte) throws MessagingException, IOException {
        if (parte.isMimeType("text/plain") && !Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition())) {
            return String.valueOf(parte.getContent());
        }
        if (parte.isMimeType("multipart/*") && parte.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String texto = textoSimples(multipart.getBodyPart(i));
                if (!texto.isBlank()) {
                    return texto;
                }
            }
        }
        return "";
    }

    static String semCitacao(String corpo) {
        List<String> linhas = corpo.replace("\r\n", "\n").lines().toList();
        int fim = linhas.size();
        for (int i = 0; i < linhas.size(); i++) {
            String linha = linhas.get(i);
            String comProxima = i + 1 < linhas.size() ? linha + " " + linhas.get(i + 1) : linha;
            if (LINHA_CITADA.matcher(linha).find() || linha.equals(DELIMITADOR_ASSINATURA)
                    || SEPARADOR_OUTLOOK.matcher(linha.strip()).matches()
                    || CABECALHO_CITACAO.matcher(linha.strip()).matches()
                    // O Gmail quebra o cabecalho longo em duas linhas.
                    || (CABECALHO_CITACAO.matcher(comProxima.strip()).matches() && LINHA_CITADA.matcher(proximaNaoVazia(linhas, i + 2)).find())) {
                fim = i;
                break;
            }
        }
        String resposta = String.join("\n", linhas.subList(0, fim)).strip();
        return resposta.isEmpty() ? escritoAbaixoDaCitacao(linhas.subList(fim, linhas.size())) : resposta;
    }

    // Resposta escrita abaixo da citacao ("> ..."): o que nao for citado depois da primeira linha citada.
    private static String escritoAbaixoDaCitacao(List<String> linhas) {
        int primeiraCitada = 0;
        while (primeiraCitada < linhas.size() && !LINHA_CITADA.matcher(linhas.get(primeiraCitada)).find()) {
            primeiraCitada++;
        }
        return String.join("\n", linhas.subList(primeiraCitada, linhas.size()).stream()
                .filter(linha -> !LINHA_CITADA.matcher(linha).find())
                .takeWhile(linha -> !linha.equals(DELIMITADOR_ASSINATURA))
                .toList()).strip();
    }

    private static String proximaNaoVazia(List<String> linhas, int inicio) {
        for (int i = inicio; i < linhas.size(); i++) {
            if (!linhas.get(i).isBlank()) {
                return linhas.get(i);
            }
        }
        return "";
    }
}
