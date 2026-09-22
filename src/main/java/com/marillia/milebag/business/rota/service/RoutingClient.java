package com.marillia.milebag.business.rota.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marillia.milebag.business.rota.model.Coordenada;
import com.marillia.milebag.business.rota.model.RotaAlternativa;
import com.marillia.milebag.core.config.ValhallaProperties;

/**
 * Calcula alternativas de trajeto via Valhalla (endpoint {@code /route}, ver
 * research.md da feature 001, secao 5) - distancia, tempo estimado e o
 * indicador agregado de pedagio (FR-001/FR-002).
 *
 * <p>Falha ou indisponibilidade do motor de rotas nao e um erro desta HU:
 * o Edge Case do spec.md preve areas sem cobertura suficiente, resolvido
 * deixando o motorista tracar rota propria - por isso este cliente devolve
 * lista vazia em vez de lancar excecao.</p>
 */
@Component
public class RoutingClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingClient.class);

    private final RestClient restClient;

    public RoutingClient(ValhallaProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    public List<RotaAlternativa> calcularAlternativas(Coordenada origem, Coordenada destino) {
        Map<String, Object> corpo = Map.of(
                "locations", List.of(
                        Map.of("lat", origem.latitude(), "lon", origem.longitude()),
                        Map.of("lat", destino.latitude(), "lon", destino.longitude())),
                "costing", "auto",
                "alternates", 2,
                "units", "kilometers");

        try {
            RespostaRota resposta = restClient.post()
                    .uri("/route")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(RespostaRota.class);

            if (resposta == null || resposta.trip() == null) {
                return List.of();
            }

            List<RotaAlternativa> alternativas = new ArrayList<>();
            alternativas.add(converter("principal", resposta.trip()));
            if (resposta.alternates() != null) {
                int indice = 1;
                for (AlternativaRota alternativa : resposta.alternates()) {
                    alternativas.add(converter("alternativa-" + indice++, alternativa.trip()));
                }
            }
            return alternativas;
        } catch (RestClientException e) {
            log.warn("Falha ao calcular rota via Valhalla - motorista podera tracar rota propria.", e);
            return List.of();
        }
    }

    private RotaAlternativa converter(String id, Trip trip) {
        Sumario sumario = trip.summary();
        double distanciaMetros = sumario.length() * 1000.0;
        long duracaoSegundos = Math.round(sumario.time());
        return new RotaAlternativa(id, distanciaMetros, duracaoSegundos, sumario.hasToll());
    }

    private record RespostaRota(Trip trip, List<AlternativaRota> alternates) {
    }

    private record AlternativaRota(Trip trip) {
    }

    private record Trip(Sumario summary) {
    }

    private record Sumario(double time, double length, @JsonProperty("has_toll") boolean hasToll) {
    }
}
