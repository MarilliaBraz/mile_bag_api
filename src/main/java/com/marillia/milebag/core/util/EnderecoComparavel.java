package com.marillia.milebag.core.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Endereco de entrega reduzido ao que identifica o local (rua, numero, quadra e lote, e a cidade quando informada),
 * para reconhecer BDOs diferentes que vao para o mesmo lugar (bagagem carona). Ignora acento, pontuacao, maiusculas,
 * abreviacoes ("Av." = "Avenida"), bairro e complemento (apartamento, bloco, sala). O CEP, quando os dois lados o
 * informam, separa enderecos iguais em setores diferentes (a "Rua 10, 25" existe em varios setores de Goiania).
 *
 * <p>Sem numero e sem quadra o endereco e vago demais para comparar: {@link #de} devolve vazio.</p>
 */
public final class EnderecoComparavel {

    private static final Pattern CEP = Pattern.compile("\\b\\d{5}-?\\d{3}\\b");
    private static final Pattern LETRA_HIFEN_NUMERO = Pattern.compile("\\b([A-Z])\\s*-\\s*(\\d)");
    private static final Pattern SEM_NUMERO = Pattern.compile("\\bS\\s*/\\s*N\\b");
    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern NUMERO_PREDIAL = Pattern.compile("\\d+[A-Z]?");

    /** So na primeira palavra (o tipo do logradouro): "Rua R" e uma rua chamada R. */
    private static final Map<String, String> ABREVIACOES_DE_TIPO = Map.ofEntries(
            Map.entry("R", "RUA"), Map.entry("AV", "AVENIDA"), Map.entry("AVN", "AVENIDA"),
            Map.entry("AL", "ALAMEDA"), Map.entry("TV", "TRAVESSA"), Map.entry("TRAV", "TRAVESSA"),
            Map.entry("PC", "PRACA"), Map.entry("PCA", "PRACA"), Map.entry("ROD", "RODOVIA"),
            Map.entry("EST", "ESTRADA"), Map.entry("ESTR", "ESTRADA"));
    private static final Map<String, String> ABREVIACOES =
            Map.of("QD", "QUADRA", "QDA", "QUADRA", "LT", "LOTE");
    private static final Set<String> TIPOS = Set.of("RUA", "AVENIDA", "ALAMEDA", "TRAVESSA", "PRACA", "RODOVIA", "ESTRADA");
    // Sem "E": "Rua E" e nome de rua em Goiania.
    private static final Set<String> PREPOSICOES = Set.of("DE", "DA", "DO", "DAS", "DOS");
    private static final Set<String> MARCAS_DE_NUMERO = Set.of("N", "NO", "NRO", "NUM", "NUMERO");
    private static final Set<String> COMPLEMENTOS =
            Set.of("APTO", "APT", "AP", "APARTAMENTO", "BLOCO", "BL", "SALA", "CASA", "ANDAR", "TORRE", "LOJA", "LJ");
    private static final String SN = "SN";

    private final String logradouro;
    private final String numero;
    private final String quadra;
    private final String lote;
    private final String cidade;
    private final String cep;

    private EnderecoComparavel(String logradouro, String numero, String quadra, String lote, String cidade, String cep) {
        this.logradouro = logradouro;
        this.numero = numero;
        this.quadra = quadra;
        this.lote = lote;
        this.cidade = cidade;
        this.cep = cep;
    }

    /** {@code cidade}: a do BDO, quando informada (opcional). */
    public static Optional<EnderecoComparavel> de(String endereco, String cidade) {
        return de(endereco, cidade, null);
    }

    /** {@code cep}: o do BDO (opcional); sem ele, vale o CEP escrito no endereco, se houver. */
    public static Optional<EnderecoComparavel> de(String endereco, String cidade, String cep) {
        List<String> palavras = palavras(endereco);
        if (palavras.isEmpty()) {
            return Optional.empty();
        }
        int i = 0;
        List<String> nome = new ArrayList<>();
        if (TIPOS.contains(palavras.get(0)) && palavras.size() > 1) {
            // O tipo nao entra na comparacao ("Goias 100" = "Av. Goias 100"), mas a palavra seguinte e sempre o nome,
            // mesmo sendo numero ("Rua 10", "Av. T-63").
            if (!fazParteDoNome(palavras.get(1)) && !palavras.get(1).chars().anyMatch(Character::isDigit)) {
                return Optional.empty();
            }
            nome.add(palavras.get(1));
            i = 2;
        }
        while (i < palavras.size() && fazParteDoNome(palavras.get(i))) {
            nome.add(palavras.get(i++));
        }
        while (i < palavras.size() && MARCAS_DE_NUMERO.contains(palavras.get(i))) {
            i++;
        }
        String numero = i < palavras.size() && NUMERO_PREDIAL.matcher(palavras.get(i)).matches() ? palavras.get(i) : null;
        String quadra = depoisDe("QUADRA", palavras);
        String lote = depoisDe("LOTE", palavras);
        if (nome.isEmpty() || (numero == null && quadra == null)) {
            return Optional.empty();
        }
        List<String> cidadeNormalizada = palavras(cidade);
        return Optional.of(new EnderecoComparavel(String.join(" ", nome), numero, quadra, lote,
                cidadeNormalizada.isEmpty() ? null : String.join(" ", cidadeNormalizada), cepDe(cep, endereco)));
    }

    /** Mesma rua, numero, quadra e lote; a cidade e o CEP so separam quando os dois lados os informam. */
    public boolean mesmoLocal(EnderecoComparavel outro) {
        boolean cidadesCompativeis = cidade == null || outro.cidade == null || cidade.equals(outro.cidade);
        boolean cepsCompativeis = cep == null || outro.cep == null || cep.equals(outro.cep);
        return cidadesCompativeis
                && cepsCompativeis
                && logradouro.equals(outro.logradouro)
                && Objects.equals(numero, outro.numero)
                && Objects.equals(quadra, outro.quadra)
                && Objects.equals(lote, outro.lote);
    }

    private static boolean fazParteDoNome(String palavra) {
        return palavra.chars().noneMatch(Character::isDigit)
                && !MARCAS_DE_NUMERO.contains(palavra)
                && !COMPLEMENTOS.contains(palavra)
                && !palavra.equals("QUADRA") && !palavra.equals("LOTE") && !palavra.equals(SN);
    }

    /** Os 8 digitos do CEP informado ou, sem ele, do CEP escrito no endereco; nulo se nenhum. */
    private static String cepDe(String cep, String endereco) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() == 8) {
            return digitos;
        }
        if (endereco != null) {
            var achado = CEP.matcher(endereco);
            if (achado.find()) {
                return achado.group().replaceAll("\\D", "");
            }
        }
        return null;
    }

    private static String depoisDe(String marca, List<String> palavras) {
        int posicao = palavras.indexOf(marca);
        return posicao >= 0 && posicao + 1 < palavras.size() ? palavras.get(posicao + 1) : null;
    }

    /** Maiusculas sem acento, sem CEP, "T-63" vira "T63", abreviacoes por extenso e sem preposicoes. */
    private static List<String> palavras(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String t = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        t = CEP.matcher(t).replaceAll(" ");
        t = LETRA_HIFEN_NUMERO.matcher(t).replaceAll("$1$2");
        t = SEM_NUMERO.matcher(t).replaceAll(" " + SN + " ");
        List<String> palavras = new ArrayList<>();
        for (String palavra : NAO_ALFANUMERICO.split(t.strip())) {
            Map<String, String> abreviacoes = palavras.isEmpty() ? ABREVIACOES_DE_TIPO : ABREVIACOES;
            String porExtenso = abreviacoes.getOrDefault(palavra, palavra);
            if (!palavra.isEmpty() && !PREPOSICOES.contains(porExtenso)) {
                palavras.add(porExtenso);
            }
        }
        return palavras;
    }
}
