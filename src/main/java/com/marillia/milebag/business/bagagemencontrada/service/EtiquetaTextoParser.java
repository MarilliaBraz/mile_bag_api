package com.marillia.milebag.business.bagagemencontrada.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLeitura;

/**
 * Extrai os campos do texto impresso numa etiqueta de bagagem IATA (HU34). Heuristica de melhor esforco, como a do BDO
 * ({@code CamposBdoExtractor}): o que nao for reconhecido fica vazio para a conferencia.
 *
 * <ul>
 * <li>numero de 10 digitos (o do codigo de barras, as vezes impresso em grupos: {@code 0 957 123456});</li>
 * <li>placa: companhia de 2 posicoes + 6 digitos ({@code LA 123456}), que tambem da a companhia;</li>
 * <li>voo: 2 posicoes + 3 ou 4 digitos ({@code LA3456}, {@code G3 1234});</li>
 * <li>destino: o primeiro codigo de 3 letras que seja um aeroporto conhecido;</li>
 * <li>nome: {@code SOBRENOME/NOME}.</li>
 * </ul>
 */
final class EtiquetaTextoParser {

    private static final String CODIGO_COMPANHIA = "([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z])";
    private static final Pattern NUMERO_10 = Pattern.compile("(?<![0-9])([0-9])\\s?([0-9]{3})\\s?([0-9]{6})(?![0-9])");
    private static final Pattern PLACA = Pattern.compile("(?<![A-Z0-9])" + CODIGO_COMPANHIA + "\\s?([0-9]{6})(?![0-9])");
    private static final Pattern VOO = Pattern.compile("(?<![A-Z0-9])" + CODIGO_COMPANHIA + "\\s?([0-9]{3,4})(?![0-9])");
    private static final Pattern TRES_LETRAS = Pattern.compile("(?<![A-Z])([A-Z]{3})(?![A-Z])");
    private static final Pattern NOME = Pattern.compile("(?<![A-Z])([A-Z]{2,})\\s?/\\s?([A-Z]{2,})(?![A-Z])");

    private EtiquetaTextoParser() {
    }

    /** {@code aeroportos}: codigos IATA conhecidos - so eles contam como destino (evita confundir com palavras de 3 letras). */
    static LeituraEtiqueta extrair(String texto, Set<String> aeroportos) {
        if (texto == null || texto.isBlank()) {
            return LeituraEtiqueta.vazia(texto);
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);

        String numero = null;
        String companhia = null;
        Matcher dez = NUMERO_10.matcher(normalizado);
        if (dez.find()) {
            numero = dez.group(1) + dez.group(2) + dez.group(3);
        }
        Matcher placa = PLACA.matcher(normalizado);
        if (placa.find()) {
            companhia = placa.group(1);
            if (numero == null) {
                numero = placa.group(1) + placa.group(2);
            }
        }
        String voo = null;
        Matcher matcherVoo = VOO.matcher(normalizado);
        if (matcherVoo.find()) {
            voo = matcherVoo.group(1) + matcherVoo.group(2);
            if (companhia == null) {
                companhia = matcherVoo.group(1);
            }
        }
        String destino = null;
        Matcher tres = TRES_LETRAS.matcher(normalizado);
        while (destino == null && tres.find()) {
            if (aeroportos.contains(tres.group(1))) {
                destino = tres.group(1);
            }
        }
        Matcher nome = NOME.matcher(normalizado);
        String nomePassageiro = nome.find() ? nome.group(1) + "/" + nome.group(2) : null;

        OrigemLeitura origem = numero != null ? OrigemLeitura.TEXTO : OrigemLeitura.NENHUMA;
        return new LeituraEtiqueta(numero, companhia, destino, voo, nomePassageiro, origem, texto);
    }
}
