package com.marillia.milebag.core.geo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.marillia.milebag.core.config.NominatimProperties;

/**
 * Resolve um endereco em coordenadas, via Nominatim (API {@code /search},
 * ver research.md da feature 001, secao 5). Cliente de infraestrutura puro
 * (sem regra de negocio) - por isso vive em {@code core}, nao em {@code
 * business.rota}, reaproveitado tambem por business.entrega.bagagemcarona.
 * Falha de geocodificacao (endereco nao encontrado, ou servico
 * indisponivel) e reportada como {@link Optional#empty()} - quem decide se
 * isso vira um erro e cada chamador (ex.: {@code RotaService}, FR-006).
 */
@Component
public class GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(GeocodingClient.class);

    private final RestClient restClient;
    private final NominatimProperties properties;

    public GeocodingClient(NominatimProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    public Optional<Coordenada> geocodificar(String endereco) {
        try {
            List<ResultadoNominatim> resultados = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("q", endereco)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ResultadoNominatim>>() {
                    });

            if (resultados == null || resultados.isEmpty()) {
                return Optional.empty();
            }

            ResultadoNominatim resultado = resultados.get(0);
            return Optional.of(new Coordenada(Double.parseDouble(resultado.lat()), Double.parseDouble(resultado.lon())));
        } catch (RestClientException | NumberFormatException e) {
            log.warn("Falha ao geocodificar endereco via Nominatim: '{}'.", endereco, e);
            return Optional.empty();
        }
    }

    /**
     * V52: a cidade do endereco, pelo mesmo servico (com {@code addressdetails}) - para o BDO antigo que nao traz a
     * cidade separada. Vazio se o servico nao achar; {@link ServicoMapaIndisponivelException} se ele estiver fora ou
     * recusar (limite de uso) - quem chama nao pode tratar a falha como "outra cidade".
     */
    public Optional<String> cidadeDe(String endereco) {
        try {
            List<ResultadoComEndereco> resultados = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("q", endereco)
                            .queryParam("format", "json")
                            .queryParam("addressdetails", 1)
                            .queryParam("limit", 1)
                            .build())
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ResultadoComEndereco>>() {
                    });
            if (resultados == null || resultados.isEmpty() || resultados.get(0).address() == null) {
                return Optional.empty();
            }
            Map<String, String> partes = resultados.get(0).address();
            for (String chave : List.of("city", "town", "municipality", "village")) {
                String cidade = partes.get(chave);
                if (cidade != null && !cidade.isBlank()) {
                    return Optional.of(cidade);
                }
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Falha ao descobrir a cidade do endereco via Nominatim: '{}'.", endereco, e);
            throw new ServicoMapaIndisponivelException(e);
        }
    }

    private record ResultadoNominatim(String lat, String lon) {
    }

    private record ResultadoComEndereco(Map<String, String> address) {
    }
}
