package com.marillia.milebag.core.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class GeometriaRotaTest {

    // Tracado leste-oeste ao longo da latitude -16 (aprox. 107 km entre os extremos).
    private final List<Coordenada> tracado = List.of(new Coordenada(-16.0, -49.0), new Coordenada(-16.0, -48.0));

    private List<Coordenada> proximos(List<Coordenada> candidatos, double raio) {
        return GeometriaRota.proximosDoTracado(tracado, candidatos, c -> c, raio);
    }

    @Test
    void pontoSobreOTracadoEEncontrado() {
        assertThat(proximos(List.of(new Coordenada(-16.0, -48.5)), 150)).hasSize(1);
    }

    @Test
    void pontoADezenasDeMetrosDentroDoRaioEOutroCentenasForaDele() {
        // 0,0005 grau de latitude ~ 55 m; 0,003 ~ 334 m.
        Coordenada perto = new Coordenada(-16.0005, -48.5);
        Coordenada longe = new Coordenada(-16.003, -48.5);

        assertThat(proximos(List.of(perto, longe), 150)).containsExactly(perto);
    }

    @Test
    void pontoAlemDaPontaDoTracadoContaPelaDistanciaAoExtremo() {
        Coordenada depoisDoFim = new Coordenada(-16.0, -47.99); // ~1,07 km depois do ultimo ponto

        assertThat(proximos(List.of(depoisDoFim), 150)).isEmpty();
        assertThat(proximos(List.of(depoisDoFim), 2_000)).hasSize(1);
    }

    @Test
    void tracadoCurtoOuVazioNaoEncontraNada() {
        assertThat(GeometriaRota.proximosDoTracado(List.of(new Coordenada(-16, -49)), List.of(new Coordenada(-16, -49)), c -> c, 150)).isEmpty();
        assertThat(GeometriaRota.proximosDoTracado(null, List.of(new Coordenada(-16, -49)), c -> c, 150)).isEmpty();
    }

    @Test
    void simplificarTiraPontosEmLinhaRetaEMantemAsCurvas() {
        // Reta de oeste para leste com um ponto a cada ~10 m, depois uma curva de ~1 km para o sul.
        List<Coordenada> reta = new java.util.ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            reta.add(new Coordenada(-16.0, -49.0 + i * 0.0001));
        }
        Coordenada curva = new Coordenada(-16.01, -48.99);
        reta.add(curva);

        List<Coordenada> simplificado = GeometriaRota.simplificar(reta, 5);

        assertThat(simplificado).containsExactly(reta.get(0), reta.get(100), curva);
    }

    @Test
    void simplificarNaoAfastaMaisQueATolerancia() {
        // Ponto a ~3 m da reta some com tolerancia de 5 m e fica com tolerancia de 1 m.
        List<Coordenada> quaseReta = List.of(new Coordenada(-16.0, -49.0), new Coordenada(-16.000027, -48.995), new Coordenada(-16.0, -48.99));

        assertThat(GeometriaRota.simplificar(quaseReta, 5)).hasSize(2);
        assertThat(GeometriaRota.simplificar(quaseReta, 1)).hasSize(3);
    }

    @Test
    void simplificarTracadoCurtoOuNuloDevolveOQueVeio() {
        assertThat(GeometriaRota.simplificar(null, 5)).isEmpty();
        assertThat(GeometriaRota.simplificar(tracado, 5)).isEqualTo(tracado);
    }
}
