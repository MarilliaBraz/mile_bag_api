package com.marillia.milebag.core.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class Polyline6Test {

    /** Codificador de referencia (mesmo algoritmo do Valhalla, precisao 1e6) so para o teste de ida e volta. */
    private static String codificar(List<Coordenada> pontos) {
        StringBuilder saida = new StringBuilder();
        long ultimaLat = 0;
        long ultimaLon = 0;
        for (Coordenada ponto : pontos) {
            long lat = Math.round(ponto.latitude() * 1e6);
            long lon = Math.round(ponto.longitude() * 1e6);
            escrever(lat - ultimaLat, saida);
            escrever(lon - ultimaLon, saida);
            ultimaLat = lat;
            ultimaLon = lon;
        }
        return saida.toString();
    }

    private static void escrever(long valor, StringBuilder saida) {
        long v = valor < 0 ? ~(valor << 1) : valor << 1;
        while (v >= 0x20) {
            saida.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        saida.append((char) (v + 63));
    }

    @Test
    void decodificaOQueFoiCodificadoComPontosNegativosEDeltasGrandes() {
        List<Coordenada> original = List.of(
                new Coordenada(-16.686900, -49.264800), new Coordenada(-16.687012, -49.263911), new Coordenada(-16.328600, -48.953400),
                new Coordenada(-15.793900, -47.882800));

        List<Coordenada> decodificado = Polyline6.decodificar(codificar(original));

        assertThat(decodificado).hasSize(original.size());
        for (int i = 0; i < original.size(); i++) {
            assertThat(decodificado.get(i).latitude()).isEqualTo(original.get(i).latitude(), org.assertj.core.data.Offset.offset(1e-6));
            assertThat(decodificado.get(i).longitude()).isEqualTo(original.get(i).longitude(), org.assertj.core.data.Offset.offset(1e-6));
        }
    }

    @Test
    void textoVazioOuNuloNaoTemPontos() {
        assertThat(Polyline6.decodificar("")).isEmpty();
        assertThat(Polyline6.decodificar(null)).isEmpty();
    }
}
