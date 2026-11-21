package com.marillia.milebag.business.viagem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.service.AeroportoService;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.business.configuracao.service.BaseAeroportuariaService;
import com.marillia.milebag.business.viagem.model.RegraCobranca;
import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.GeocodingClient;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.RoutingClient;
import com.marillia.milebag.core.geo.ServicoMapaIndisponivelException;

/**
 * Como a viagem e paga (V52, item 7). A base do BDO e o aeroporto de retirada dele, se for uma base da empresa, ou a
 * base principal. Entrega na cidade da base: valor fixo. Fora dela: km, com a ida (aeroporto -> endereco) e a volta
 * calculada de verdade (endereco -> aeroporto). A navegacao do motorista continua saindo de onde ele esta - so a conta
 * parte do aeroporto.
 */
@Service
public class CobrancaViagemService {

    private static final Logger log = LoggerFactory.getLogger(CobrancaViagemService.class);

    private final BaseAeroportuariaService baseAeroportuariaService;
    private final AeroportoService aeroportoService;
    private final RoutingClient routingClient;
    private final GeocodingClient geocodingClient;
    /** Mais que isso e sinal de uso fora do esperado: o cache recomeca em vez de crescer sem limite. */
    private static final int LIMITE_CACHE = 2_000;

    /**
     * Cidade descoberta pelo mapa para enderecos sem cidade separada - so respostas do servico (achou ou nao achou);
     * uma falha nunca entra aqui, para ser tentada de novo no proximo calculo.
     */
    private final Map<String, Optional<String>> cidadesDescobertas = new ConcurrentHashMap<>();

    public CobrancaViagemService(BaseAeroportuariaService baseAeroportuariaService, AeroportoService aeroportoService,
            RoutingClient routingClient, GeocodingClient geocodingClient) {
        this.baseAeroportuariaService = baseAeroportuariaService;
        this.aeroportoService = aeroportoService;
        this.routingClient = routingClient;
        this.geocodingClient = geocodingClient;
    }

    /**
     * A base e a regra de uma viagem; {@code base} vazia quando a empresa nao cadastrou nenhuma base. {@code regra} nula:
     * nao deu para saber agora (servico de mapa fora) - a viagem nao e congelada e o valor fica pendente.
     */
    public record Cobranca(Optional<Aeroporto> base, RegraCobranca regra) {
    }

    /** A base do BDO: o aeroporto de retirada dele, se for base da empresa; senao a principal. */
    public Optional<Aeroporto> baseDe(Bdo bdo) {
        List<BaseAeroportuaria> bases = baseAeroportuariaService.listar();
        if (bases.isEmpty()) {
            return Optional.empty();
        }
        String doBdo = bdo.getAeroportoRetirada() == null ? "" : bdo.getAeroportoRetirada();
        BaseAeroportuaria escolhida = bases.stream()
                .filter(base -> base.getCodigoIata().equalsIgnoreCase(doBdo))
                .findFirst()
                .or(() -> bases.stream().filter(BaseAeroportuaria::isPrincipal).findFirst())
                .orElse(bases.get(0));
        Optional<Aeroporto> aeroporto = aeroportoService.buscar(escolhida.getCodigoIata().toUpperCase(Locale.ROOT));
        if (aeroporto.isEmpty()) {
            log.warn("Base {} da empresa nao esta no cadastro de aeroportos - viagem paga por km.", escolhida.getCodigoIata());
        }
        return aeroporto;
    }

    /**
     * Base e regra do BDO. Sem base cadastrada, ou sem cidade conhecida para a entrega, paga por km. Com o servico de mapa
     * fora, a regra fica nula (indeterminada) - nunca vira "fora da cidade" por uma falha.
     */
    public Cobranca cobrancaDe(Bdo bdo) {
        Optional<Aeroporto> base = baseDe(bdo);
        if (base.isEmpty()) {
            return new Cobranca(base, RegraCobranca.POR_KM);
        }
        try {
            return new Cobranca(base, naCidadeDaBase(bdo, base.get()) ? RegraCobranca.FIXO_CIDADE_BASE : RegraCobranca.POR_KM);
        } catch (ServicoMapaIndisponivelException e) {
            return new Cobranca(base, null);
        }
    }

    /**
     * A cidade do BDO (campo proprio, V49). Nos BDOs antigos, so com o endereco: a cidade escrita como cidade (", GOIANIA
     * - GO") e, se nao der para saber pelo texto, a cidade que o mapa da para o endereco.
     */
    boolean naCidadeDaBase(Bdo bdo, Aeroporto aeroporto) {
        String cidadeBase = aeroporto.getCidade();
        String base = normalizar(cidadeBase);
        // Cidades homonimas em outro estado ("Bom Jesus"): com a UF dos dois lados, ela tambem tem de bater.
        if (bdo.getUf() != null && aeroporto.getUf() != null && !bdo.getUf().equalsIgnoreCase(aeroporto.getUf())) {
            return false;
        }
        if (bdo.getCidade() != null && !bdo.getCidade().isBlank()) {
            return normalizar(bdo.getCidade()).equals(base);
        }
        String endereco = bdo.getEnderecoEntrega();
        if (endereco == null || endereco.isBlank()) {
            return false;
        }
        if (enderecoNaCidade(endereco, cidadeBase)) {
            return true;
        }
        return cidadeDoMapa(endereco).map(base::equals).orElse(false);
    }

    /** A cidade pelo mapa, com cache so das respostas; a chamada fica fora do lock do mapa. Falha propaga. */
    private Optional<String> cidadeDoMapa(String endereco) {
        String chave = normalizar(endereco);
        Optional<String> conhecida = cidadesDescobertas.get(chave);
        if (conhecida != null) {
            return conhecida;
        }
        Optional<String> cidade = geocodingClient.cidadeDe(endereco).map(CobrancaViagemService::normalizar);
        if (cidadesDescobertas.size() >= LIMITE_CACHE) {
            cidadesDescobertas.clear();
        }
        cidadesDescobertas.put(chave, cidade);
        return cidade;
    }

    /**
     * Ida (aeroporto -> destino) e volta (destino -> aeroporto) pelo motor de rotas, em km. Vazio se o motor nao
     * responder - quem chama usa o km da rota do motorista, como antes.
     */
    public Optional<BigDecimal[]> kmIdaEVolta(Aeroporto base, Coordenada destino) {
        Coordenada aeroporto = new Coordenada(base.getLatitude(), base.getLongitude());
        Optional<BigDecimal> ida = menorDistancia(aeroporto, destino);
        Optional<BigDecimal> volta = ida.isPresent() ? menorDistancia(destino, aeroporto) : Optional.empty();
        if (ida.isEmpty() || volta.isEmpty()) {
            log.warn("Ida e volta a partir de {} nao calculadas (motor de rotas sem resposta) - vale o km da rota do motorista.",
                    base.getCodigo());
            return Optional.empty();
        }
        return Optional.of(new BigDecimal[] {ida.get(), volta.get()});
    }

    private Optional<BigDecimal> menorDistancia(Coordenada origem, Coordenada destino) {
        try {
            return routingClient.calcularAlternativas(origem, destino).stream()
                    .map(RotaAlternativa::distanciaMetros)
                    .min(Double::compare)
                    .map(metros -> BigDecimal.valueOf(metros / 1000.0).setScale(3, RoundingMode.HALF_UP));
        } catch (RuntimeException e) {
            log.warn("Falha ao calcular a distancia {} -> {}.", origem, destino, e);
            return Optional.empty();
        }
    }

    /** A cidade escrita como cidade (", GOIANIA - GO", ", GOIANIA,") e nao como parte de outro nome ("APARECIDA DE GOIANIA"). */
    static boolean enderecoNaCidade(String endereco, String cidade) {
        if (endereco == null || cidade == null || cidade.isBlank()) {
            return false;
        }
        return Pattern.compile("(?:^|,\\s*)" + Pattern.quote(normalizar(cidade)) + "\\s*(?:[-/]\\s*[A-Z]{2}\\b|,|$)")
                .matcher(normalizar(endereco))
                .find();
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        // Hifen e apostrofo viram espaco ("Mogi-Mirim" = "Mogi Mirim", "D'Oeste" = "D Oeste").
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("(?<=[A-Z])[-'’](?=[A-Z])", " ").replaceAll("\\s+", " ").strip();
    }
}
