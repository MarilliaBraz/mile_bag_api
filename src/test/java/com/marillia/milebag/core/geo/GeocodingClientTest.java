package com.marillia.milebag.core.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** V57: a cidade da praca vem do endereco do Nominatim, como "Cidade/UF". */
class GeocodingClientTest {

    @Test
    void montaCidadeComUf() {
        assertThat(GeocodingClient.cidadeComUf(Map.of("city", "Campinas", "ISO3166-2-lvl4", "BR-SP"))).contains("Campinas/SP");
    }

    @Test
    void usaCidadePequenaQuandoNaoHaCity() {
        assertThat(GeocodingClient.cidadeComUf(Map.of("town", "Itupeva", "ISO3166-2-lvl4", "BR-SP"))).contains("Itupeva/SP");
    }

    @Test
    void semUfFicaSoACidade() {
        assertThat(GeocodingClient.cidadeComUf(Map.of("city", "Assuncion"))).contains("Assuncion");
    }

    @Test
    void semCidadeFicaVazio() {
        assertThat(GeocodingClient.cidadeComUf(Map.of("state", "Sao Paulo"))).isEmpty();
        assertThat(GeocodingClient.cidadeComUf(null)).isEmpty();
    }
}
