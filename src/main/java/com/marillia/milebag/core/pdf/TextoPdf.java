package com.marillia.milebag.core.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Texto nas fontes padrao do PDF (relatorios do fechamento, HU13; ficha da bagagem encontrada, HU36). */
public final class TextoPdf {

    private TextoPdf() {
    }

    /** As fontes padrao do PDF so cobrem WinAnsi; texto livre do tenant (emoji, tab) derrubaria a geracao inteira. */
    public static String somenteCaracteresDaFonte(PDType1Font fonte, String texto) {
        StringBuilder seguro = new StringBuilder(texto.length());
        texto.codePoints().forEach(codigo -> {
            String caractere = new String(Character.toChars(codigo));
            try {
                fonte.encode(caractere);
                seguro.append(caractere);
            } catch (IOException | IllegalArgumentException e) {
                seguro.append(codigo == '\t' ? ' ' : '?');
            }
        });
        return seguro.toString();
    }

    /** Quebra o texto em linhas que cabem na largura (em pontos); uma palavra maior que a linha fica sozinha nela. */
    public static List<String> quebrar(PDType1Font fonte, float tamanho, String texto, float largura) throws IOException {
        List<String> linhas = new ArrayList<>();
        for (String paragrafo : somenteCaracteresDaFonte(fonte, texto).split("\\R")) {
            StringBuilder linha = new StringBuilder();
            for (String palavra : paragrafo.trim().split("\\s+")) {
                String candidata = linha.isEmpty() ? palavra : linha + " " + palavra;
                if (!linha.isEmpty() && fonte.getStringWidth(candidata) / 1000 * tamanho > largura) {
                    linhas.add(linha.toString());
                    linha = new StringBuilder(palavra);
                } else {
                    linha = new StringBuilder(candidata);
                }
            }
            linhas.add(linha.toString());
        }
        return linhas;
    }
}
