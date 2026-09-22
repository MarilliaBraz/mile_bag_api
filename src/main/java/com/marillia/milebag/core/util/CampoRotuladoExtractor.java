package com.marillia.milebag.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai o valor de um campo "rotulo: valor" de um texto de origem livre.
 * Reaproveitado por {@code BdoOcrService} (texto vindo do OCR, HU01) e
 * {@code BdoTelexParser} (texto vindo direto do corpo do e-mail, HU02) - a
 * mesma heuristica de reconhecimento do padrao WorldTracer, independente de
 * o texto ter vindo de imagem ou de e-mail (CONVENTIONS.md secao 1.1,
 * "comportamento comum a multiplos dominios vive em core").
 */
public final class CampoRotuladoExtractor {

    private CampoRotuladoExtractor() {
    }

    public static String extrair(String textoBruto, String... rotulos) {
        if (textoBruto == null) {
            return null;
        }
        for (String rotulo : rotulos) {
            Pattern padrao = Pattern.compile("(?im)^\\s*" + Pattern.quote(rotulo) + "\\s*[:/-]\\s*(.+)$");
            Matcher correspondencia = padrao.matcher(textoBruto);
            if (correspondencia.find()) {
                String valor = correspondencia.group(1).trim();
                if (!valor.isEmpty()) {
                    return valor;
                }
            }
        }
        return null;
    }
}
