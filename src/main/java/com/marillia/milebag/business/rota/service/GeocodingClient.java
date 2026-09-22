package com.marillia.milebag.business.rota.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.marillia.milebag.business.rota.model.Coordenada;
import com.marillia.milebag.core.config.NominatimProperties;

/**
 * Resolve o endereco de entrega do BDO em coordenadas, via Nominatim (API
 * `/search`, ver research.md da feature 001, secao 5). Falha de
 * geocodificacao (endereco nao encontrado, ou servico indisponivel) e
 * reportada como {@link Optional#empty()} - quem decide se isso vira um
 * erro 422 e o {@code RotaService} (depende de haver ou nao uma coordenada
 * manual, FR-006).
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

    private record ResultadoNominatim(String lat, String lon) {
    }
}
