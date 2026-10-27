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
 *
 * <p>O rotulo casa sem diferenciar maiusculas, acentos e cedilha (o OCR e
 * os formularios das companhias variam: "Endereço", "ENDERECO",
 * "endereco") e pode ter varias palavras, separadas por qualquer espaco
 * ("Nome do passageiro"). Os rotulos sao testados na ordem recebida: os
 * mais especificos devem vir antes dos genericos.</p>
 */
public final class CampoRotuladoExtractor {

    private CampoRotuladoExtractor() {
    }

    public static String extrair(String textoBruto, String... rotulos) {
        if (textoBruto == null) {
            return null;
        }
        for (String rotulo : rotulos) {
            // (?imu): sem diferenciar maiusculas (inclusive acentuadas), uma linha por vez.
            Pattern padrao = Pattern.compile("(?imu)^\\s*" + padraoDoRotulo(rotulo) + "\\s*[:/-]\\s*(.+)$");
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

    /** Cada letra aceita as variantes acentuadas; espacos aceitam um ou mais espacos; o resto e literal. */
    private static String padraoDoRotulo(String rotulo) {
        StringBuilder padrao = new StringBuilder();
        for (char caractere : rotulo.toCharArray()) {
            padrao.append(switch (Character.toLowerCase(caractere)) {
                case 'a', 'á', 'à', 'â', 'ã' -> "[aáàâã]";
                case 'e', 'é', 'ê' -> "[eéê]";
                case 'i', 'í' -> "[ií]";
                case 'o', 'ó', 'ô', 'õ' -> "[oóôõ]";
                case 'u', 'ú', 'ü' -> "[uúü]";
                case 'c', 'ç' -> "[cç]";
                case ' ' -> "\\s+";
                default -> Pattern.quote(String.valueOf(caractere));
            });
        }
        return padrao.toString();
    }
}
