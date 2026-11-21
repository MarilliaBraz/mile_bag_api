package com.marillia.milebag.business.bdo.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.core.util.CampoRotuladoExtractor;

/**
 * Le o BDO no formato real: o telex WorldTracer ("WM BDO AHL GYNLA24713/...", "NM01 ...", "PA ...", "TN01 ...") que
 * chega no corpo do e-mail ou que a empresa cola no painel (V49). Tambem aceita, no que der, o formulario impresso de
 * algumas companhias ("File Reference:", "Customer Name:", "Delivery Address:").
 *
 * <p>Regras tiradas dos BDOs reais: cada linha comeca por um codigo de 2 letras (com numero de ordem opcional, como
 * NM01) e pode ter outros codigos depois de " ." ("ZP 74343240 .DD 15SEP .DW .CS D/BRL132"); a impressao pode trazer
 * "·" no lugar do espaco e "- " ou "»" no inicio da linha; o endereco temporario (TA/YT/TS/TZ) vale no lugar do
 * permanente (PA/YP/ST/ZP); o e-mail vem codificado (/A/ = @, /D/ = ponto, /U/ = sublinhado). Um campo nao
 * reconhecido fica vazio - quem confere e a empresa (formulario) ou a regra de campos minimos do e-mail.</p>
 */
@Component
public class LeitorTelexBdo {

    private static final Set<String> CODIGOS = Set.of(
            "NM", "GN", "PA", "YP", "ST", "CO", "ZP", "TA", "YT", "TS", "TC", "TZ", "PN", "CP", "TP", "EA", "LD", "SI",
            "TN", "CT", "FD", "RT", "DD", "DW", "DV", "CS", "DS", "UP", "OF", "SE", "TX", "AG", "BR", "CI", "CW");

    private static final Pattern CABECALHO = Pattern.compile(
            "\\bBDO\\s+([A-Z]{3})\\s+([A-Z]{5}\\d{3,7})\\b(?:\\s*/\\s*\\d{3,4}GMT\\s*/\\s*(\\d{2}[A-Z]{3}\\d{2}))?");
    private static final Pattern CODIGO_DE_BARRAS = Pattern.compile("\\b(AHL|DPR|DAH|OHD)([A-Z]{5}\\d{3,6})-\\d{2}\\b");
    private static final Pattern SEPARADOR_DE_CODIGO = Pattern.compile("\\s+(?=\\.[A-Z]{2})");
    private static final Pattern CODIGO_E_VALOR = Pattern.compile("^([A-Z]{2})(\\d{2})?(?:\\s+(.*))?$");
    /** Depois de " ." o codigo pode vir colado no valor (".YPGOIANIA .STGOIAS"). */
    private static final Pattern CODIGO_COLADO = Pattern.compile("^([A-Z]{2})(\\d{2})?\\s*(.*)$");
    private static final Pattern MALAS_NA_ASSINATURA = Pattern.compile("RECEIPT OF\\s+(\\d+)\\s+BAG");
    /** "LA3544 GYN 17:25" ou "BAG OB LA3266 14NOV26 GYN STA 13H15": o voo que traz a mala e o aeroporto de chegada. */
    private static final Pattern VOO_E_AEROPORTO =
            Pattern.compile("\\b([A-Z]{2}\\d{2,4})\\s+(?:\\d{1,2}[A-Z]{3}(?:\\d{2})?\\s+)?([A-Z]{3})\\b");
    /** Formulario da companhia: "DLV TOI GYN OB LA3518/29OCT". */
    private static final Pattern ENTREGA_EM = Pattern.compile("\\bDLV\\s+TO[IA]?\\s+([A-Z]{3})\\b");
    private static final Pattern VOO_A_BORDO = Pattern.compile("\\bOB\\s+([A-Z]{2}\\d{2,4})\\b");
    private static final Pattern EM_NOME_DE = Pattern.compile("ON BEHALF OF\\s+([A-Z][A-Z ]*[A-Z])", Pattern.MULTILINE);
    private static final Pattern VALOR = Pattern.compile("([A-Z]{3})\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern CIDADE_UF_CEP = Pattern.compile("^(.+?)\\s+([A-Z]{2})\\s+(\\d{5}-?\\d{3})\\b");
    private static final Pattern ETIQUETA_NO_FORMULARIO = Pattern.compile("BAG TAG\\s+(?:COLOR/DESC\\s+)?(\\d{10}|[A-Z]{2}\\d{6})");

    private static final DateTimeFormatter DIA_MES_ANO2 = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("ddMMMyy").toFormatter(Locale.ENGLISH);
    private static final DateTimeFormatter DIA_MES_ANO4 = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("ddMMMyyyy").toFormatter(Locale.ENGLISH);

    /** Codigo IATA da companhia, que vem no numero do processo (GYN + LA + 24713). */
    private static final Map<String, String> COMPANHIAS = Map.ofEntries(
            Map.entry("LA", "LATAM"), Map.entry("JJ", "LATAM"), Map.entry("AD", "AZUL"), Map.entry("IB", "IBERIA"),
            Map.entry("UX", "AIR EUROPA"), Map.entry("CM", "COPA AIRLINES"), Map.entry("LX", "SWISS"),
            Map.entry("UA", "UNITED AIRLINES"), Map.entry("AA", "AMERICAN AIRLINES"), Map.entry("DL", "DELTA AIR LINES"),
            Map.entry("AF", "AIR FRANCE"), Map.entry("KL", "KLM"), Map.entry("LH", "LUFTHANSA"),
            Map.entry("TP", "TAP AIR PORTUGAL"), Map.entry("AR", "AEROLINEAS ARGENTINAS"), Map.entry("AV", "AVIANCA"),
            Map.entry("EK", "EMIRATES"), Map.entry("QR", "QATAR AIRWAYS"), Map.entry("TK", "TURKISH AIRLINES"),
            Map.entry("BA", "BRITISH AIRWAYS"), Map.entry("AZ", "ITA AIRWAYS"), Map.entry("ET", "ETHIOPIAN AIRLINES"));

    private static final Map<String, String> ESTADOS = Map.ofEntries(
            Map.entry("ACRE", "AC"), Map.entry("ALAGOAS", "AL"), Map.entry("AMAPA", "AP"), Map.entry("AMAZONAS", "AM"),
            Map.entry("BAHIA", "BA"), Map.entry("CEARA", "CE"), Map.entry("DISTRITO FEDERAL", "DF"),
            Map.entry("ESPIRITO SANTO", "ES"), Map.entry("GOIAS", "GO"), Map.entry("MARANHAO", "MA"),
            Map.entry("MATO GROSSO", "MT"), Map.entry("MATO GROSSO DO SUL", "MS"), Map.entry("MINAS GERAIS", "MG"),
            Map.entry("PARA", "PA"), Map.entry("PARAIBA", "PB"), Map.entry("PARANA", "PR"), Map.entry("PERNAMBUCO", "PE"),
            Map.entry("PIAUI", "PI"), Map.entry("RIO DE JANEIRO", "RJ"), Map.entry("RIO GRANDE DO NORTE", "RN"),
            Map.entry("RIO GRANDE DO SUL", "RS"), Map.entry("RONDONIA", "RO"), Map.entry("RORAIMA", "RR"),
            Map.entry("SANTA CATARINA", "SC"), Map.entry("SAO PAULO", "SP"), Map.entry("SERGIPE", "SE"),
            Map.entry("TOCANTINS", "TO"));

    /** Telex colado ou recebido: bem acima de um BDO real (menos de 2 mil caracteres), abaixo de um abuso. */
    public static final int TAMANHO_MAXIMO_TEXTO = 20_000;
    private static final int MAXIMO_MALAS = 20;
    /** Codigos cuja linha o WorldTracer quebra quando passa da largura: a linha seguinte sem codigo continua o valor. */
    private static final Set<String> CONTINUAVEIS = Set.of("PA", "TA", "LD", "SI");

    private final Clock relogio;

    public LeitorTelexBdo() {
        this(Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    LeitorTelexBdo(Clock relogio) {
        this.relogio = relogio;
    }

    public CamposExtraidosBdo ler(String textoBruto) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return new CamposExtraidosBdo(null, null, null, null, null, null, null);
        }
        // Espacos repetidos viram um so antes de qualquer regex: sem isso, uma linha com milhares de espacos deixa a
        // separacao dos codigos quadratica. NUL sai porque o PostgreSQL o recusa em TEXT.
        String texto = textoBruto.replace('·', ' ').replace('•', ' ').replace("\u0000", "")
                .replaceAll("[ \\t\\x0B\\f]+", " ");
        String maiusculo = texto.toUpperCase(Locale.ROOT);
        Map<String, List<String>> codigos = codigos(maiusculo);

        String numero = null;
        String tipoProcesso = null;
        LocalDate dataDoTelex = null;
        Matcher cabecalho = CABECALHO.matcher(maiusculo);
        Matcher barras = CODIGO_DE_BARRAS.matcher(maiusculo);
        if (cabecalho.find()) {
            tipoProcesso = cabecalho.group(1);
            numero = cabecalho.group(2);
            dataDoTelex = data(cabecalho.group(3), DIA_MES_ANO2);
        } else if (barras.find()) {
            tipoProcesso = barras.group(1);
            numero = barras.group(2);
        } else {
            numero = semEspacos(rotulo(maiusculo, "FILE REFERENCE"));
        }

        Endereco endereco = endereco(codigos, maiusculo);
        String[] chegada = chegada(codigos, maiusculo);
        String etiquetas = juntar(primeirasPalavras(codigos.get("TN")));

        return new CamposExtraidosBdo(
                limitar(numero, 30),
                limitar(passageiro(codigos, maiusculo), 200),
                limitar(endereco.logradouro(), 300),
                limitar(companhia(numero, maiusculo), 100),
                limitar(telefone(codigos, maiusculo), 30),
                limitar(email(codigos), 200),
                limitar(etiquetas != null ? etiquetas : etiquetaDoFormulario(maiusculo), 300),
                tipoProcesso,
                limitar(endereco.cidade(), 100),
                endereco.uf(),
                endereco.cep(),
                instrucoes(codigos, maiusculo),
                chegada[0],
                limitar(chegada[1], 20),
                dataPrevista(primeiro(codigos.get("DD")), dataDoTelex),
                quantidadeMalas(codigos, maiusculo),
                limitar(juntar(primeirasPalavras(codigos.get("CT"))), 200),
                limitar(valor(primeiro(codigos.get("CS"))), 30));
    }

    /** Codigo -> valores, na ordem em que aparecem (NM01 e NM02 entram juntos em NM). */
    private static Map<String, List<String>> codigos(String texto) {
        Map<String, List<String>> codigos = new LinkedHashMap<>();
        List<String> continuavel = null;
        for (String linhaBruta : texto.split("\\R")) {
            String linha = linhaBruta.replaceFirst("^[\\s»>\\-–]+", "").strip();
            if (continuavel != null && pareceContinuacao(linha)) {
                // Endereco ou instrucao quebrados pela largura do telex: a linha de baixo continua o valor.
                continuavel.set(continuavel.size() - 1, (continuavel.get(continuavel.size() - 1) + " " + linha).strip());
                continue;
            }
            List<String> ultimo = null;
            String primeiroCodigo = null;
            boolean variosCodigos = false;
            for (String trecho : SEPARADOR_DE_CODIGO.split(linha)) {
                String parte = trecho.strip();
                boolean depoisDePonto = parte.startsWith(".");
                Matcher m = (depoisDePonto ? CODIGO_COLADO : CODIGO_E_VALOR).matcher(depoisDePonto ? parte.substring(1) : parte);
                if (m.matches() && CODIGOS.contains(m.group(1))) {
                    variosCodigos = primeiroCodigo != null;
                    primeiroCodigo = primeiroCodigo == null ? m.group(1) : primeiroCodigo;
                    ultimo = codigos.computeIfAbsent(m.group(1), k -> new ArrayList<>());
                    ultimo.add(m.group(3) == null ? "" : m.group(3).replaceAll("\\s+", " ").strip());
                } else if (ultimo != null) {
                    // " .QD 11" no meio do endereco nao e codigo: continua o valor anterior.
                    ultimo.set(ultimo.size() - 1, (ultimo.get(ultimo.size() - 1) + " " + parte.replaceAll("\\s+", " ")).strip());
                }
            }
            // So a linha de um unico codigo continuavel (PA, TA, LD, SI) pode seguir na linha de baixo.
            continuavel = primeiroCodigo != null && !variosCodigos && CONTINUAVEIS.contains(primeiroCodigo) ? ultimo : null;
        }
        return codigos;
    }

    /** Linha sem codigo, sem pontilhado e que nao e rodape: texto que sobrou da linha de cima. */
    private static boolean pareceContinuacao(String linha) {
        if (linha.isEmpty() || linha.startsWith("/") || linha.startsWith(".") || linha.startsWith("END OF")
                || linha.startsWith("FECHA") || linha.matches("[.\\s]+")) {
            return false;
        }
        Matcher m = CODIGO_E_VALOR.matcher(SEPARADOR_DE_CODIGO.split(linha)[0].strip());
        return !(m.matches() && CODIGOS.contains(m.group(1)));
    }

    private static String passageiro(Map<String, List<String>> codigos, String texto) {
        String sobrenome = primeiro(codigos.get("NM"));
        String nome = primeiro(codigos.get("GN"));
        if (sobrenome != null) {
            return nome == null ? sobrenome : nome + " " + sobrenome;
        }
        String doFormulario = rotulo(texto, "CUSTOMER NAME");
        if (doFormulario == null || !doFormulario.contains("/")) {
            return doFormulario;
        }
        // Formulario da companhia: "SOBRENOME/NOMEMRS" - o nome primeiro, sem o tratamento colado no fim.
        String[] partes = doFormulario.split("/", 2);
        String primeiroNome = partes[1].strip().replaceFirst("(MRS|MR|MS|MISS)$", "").strip();
        return primeiroNome.isEmpty() ? partes[0].strip() : primeiroNome + " " + partes[0].strip();
    }

    private record Endereco(String logradouro, String cidade, String uf, String cep) {
    }

    /** O endereco temporario, quando existe, e onde o passageiro esta: vale no lugar do permanente. */
    private static Endereco endereco(Map<String, List<String>> codigos, String texto) {
        boolean temporario = primeiro(codigos.get("TA")) != null;
        String logradouro = primeiro(codigos.get(temporario ? "TA" : "PA"));
        if (logradouro != null) {
            return new Endereco(logradouro,
                    primeiro(codigos.get(temporario ? "YT" : "YP")),
                    uf(primeiro(codigos.get(temporario ? "TS" : "ST"))),
                    cep(primeiro(codigos.get(temporario ? "TZ" : "ZP"))));
        }
        String doFormulario = rotulo(texto, "DELIVERY ADDRESS");
        if (doFormulario == null) {
            return new Endereco(null, null, null, null);
        }
        Matcher linhaSeguinte = CIDADE_UF_CEP.matcher(linhaDepois(texto, doFormulario));
        String rua = doFormulario.replaceFirst("[,\\s]+$", "");
        return linhaSeguinte.find()
                ? new Endereco(rua, linhaSeguinte.group(1).strip(), linhaSeguinte.group(2), cep(linhaSeguinte.group(3)))
                : new Endereco(rua, null, null, null);
    }

    private static String companhia(String numero, String texto) {
        if (numero != null && numero.matches("[A-Z]{5}\\d+")) {
            String nome = COMPANHIAS.get(numero.substring(3, 5));
            if (nome != null) {
                return nome;
            }
        }
        Matcher emNomeDe = EM_NOME_DE.matcher(texto);
        return emNomeDe.find() ? emNomeDe.group(1) : null;
    }

    /** Celular primeiro (CP), depois o telefone do endereco (PN) e o do endereco temporario (TP). */
    private static String telefone(Map<String, List<String>> codigos, String texto) {
        for (String codigo : List.of("CP", "PN", "TP")) {
            String telefone = primeiro(codigos.get(codigo));
            if (telefone != null) {
                return telefone;
            }
        }
        return rotulo(texto, "CONTACT NUMBERS");
    }

    /** O WorldTracer nao aceita @ nem ponto: /A/ = @, /D/ = ponto, /U/ = sublinhado, /H/ = hifen. */
    private static String email(Map<String, List<String>> codigos) {
        String codificado = primeiro(codigos.get("EA"));
        if (codificado == null) {
            return null;
        }
        return codificado.replace("/A/", "@").replace("/D/", ".").replace("/U/", "_").replace("/H/", "-")
                .replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String instrucoes(Map<String, List<String>> codigos, String texto) {
        List<String> linhas = new ArrayList<>();
        for (String codigo : List.of("LD", "SI")) {
            for (String valor : codigos.getOrDefault(codigo, List.of())) {
                String limpo = valor.replaceFirst("/+$", "").strip();
                if (!limpo.isEmpty()) {
                    linhas.add(limpo);
                }
            }
        }
        return linhas.isEmpty() ? rotulo(texto, "COMMENTS") : String.join(" | ", linhas);
    }

    /**
     * [aeroporto, voo]: onde o motorista retira a mala. Primeiro o voo que traz a mala, escrito na instrucao
     * ("LA3544 GYN 17:25"); sem ele, o destino final da rota com trechos (RT GRU/GYN) e, por ultimo, o posto que emitiu o
     * BDO. A empresa confere e corrige no formulario.
     */
    private static String[] chegada(Map<String, List<String>> codigos, String texto) {
        List<String> instrucoes = new ArrayList<>(codigos.getOrDefault("LD", List.of()));
        instrucoes.addAll(codigos.getOrDefault("SI", List.of()));
        for (String instrucao : instrucoes) {
            Matcher m = VOO_E_AEROPORTO.matcher(instrucao);
            if (m.find()) {
                return new String[] {m.group(2), m.group(1)};
            }
        }
        Matcher entregaEm = ENTREGA_EM.matcher(texto);
        if (entregaEm.find()) {
            Matcher aBordo = VOO_A_BORDO.matcher(texto);
            return new String[] {entregaEm.group(1), aBordo.find() ? aBordo.group(1) : null};
        }
        // "RT LHR" sozinho e a origem da viagem do passageiro; so a rota com trechos ("GRU/GYN") diz onde a mala chega.
        String rota = primeiraPalavra(primeiro(codigos.get("RT")));
        if (rota != null && rota.contains("/")) {
            String[] trechos = rota.split("/");
            String destino = trechos[trechos.length - 1];
            if (destino.matches("[A-Z]{3}")) {
                return new String[] {destino, null};
            }
        }
        String posto = primeiraPalavra(primeiro(codigos.get("DS")));
        return new String[] {posto != null && posto.matches("[A-Z]{3}.*") ? posto.substring(0, 3) : null, null};
    }

    private LocalDate dataPrevista(String dd, LocalDate dataDoTelex) {
        String diaMes = primeiraPalavra(dd);
        if (diaMes == null || !diaMes.matches("\\d{1,2}[A-Z]{3}")) {
            return null;
        }
        LocalDate referencia = dataDoTelex != null ? dataDoTelex : LocalDate.now(relogio);
        LocalDate data = data((diaMes.length() == 4 ? "0" + diaMes : diaMes) + referencia.getYear(), DIA_MES_ANO4);
        // Telex de dezembro com entrega em janeiro: a entrega e no ano seguinte.
        return data != null && data.isBefore(referencia.minusMonths(6)) ? data.plusYears(1) : data;
    }

    private static Integer quantidadeMalas(Map<String, List<String>> codigos, String texto) {
        int etiquetas = primeirasPalavras(codigos.get("TN")).size();
        Matcher assinatura = MALAS_NA_ASSINATURA.matcher(texto);
        // Numero absurdo na assinatura (ou que nem cabe num int) e ignorado: a empresa confere a quantidade.
        int naAssinatura = assinatura.find() && assinatura.group(1).length() <= 2 ? Integer.parseInt(assinatura.group(1)) : 0;
        int malas = Math.max(etiquetas, naAssinatura);
        return malas > 0 && malas <= MAXIMO_MALAS ? malas : null;
    }

    private static String valor(String cs) {
        if (cs == null) {
            return null;
        }
        Matcher m = VALOR.matcher(cs);
        return m.find() ? m.group(1) + " " + m.group(2) : null;
    }

    private static String etiquetaDoFormulario(String texto) {
        Matcher m = ETIQUETA_NO_FORMULARIO.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    private static String uf(String estado) {
        if (estado == null) {
            return null;
        }
        String semAcento = Normalizer.normalize(estado, Normalizer.Form.NFD).replaceAll("\\p{M}", "").strip();
        return semAcento.matches("[A-Z]{2}") ? semAcento : ESTADOS.get(semAcento);
    }

    private static String cep(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.length() < 8 ? null : digitos.substring(0, 5) + "-" + digitos.substring(5, 8);
    }

    private static LocalDate data(String texto, DateTimeFormatter formato) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto, formato);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String rotulo(String texto, String rotulo) {
        return CampoRotuladoExtractor.extrair(texto, rotulo);
    }

    private static String linhaDepois(String texto, String valor) {
        String[] linhas = texto.split("\\R");
        for (int i = 0; i < linhas.length - 1; i++) {
            if (linhas[i].contains(valor)) {
                return linhas[i + 1].strip();
            }
        }
        return "";
    }

    private static String primeiro(List<String> valores) {
        if (valores == null) {
            return null;
        }
        return valores.stream().filter(v -> !v.isBlank()).findFirst().orElse(null);
    }

    private static String primeiraPalavra(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip().split("\\s+")[0];
    }

    private static List<String> primeirasPalavras(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        return valores.stream().map(LeitorTelexBdo::primeiraPalavra).filter(Objects::nonNull).toList();
    }

    private static String juntar(List<String> valores) {
        return valores.isEmpty() ? null : String.join(", ", valores);
    }

    private static String semEspacos(String valor) {
        return valor == null ? null : valor.replaceAll("\\s+", "");
    }

    private static String limitar(String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.strip();
        return limpo.length() > maximo ? limpo.substring(0, maximo) : limpo;
    }
}
