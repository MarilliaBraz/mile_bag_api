package com.marillia.milebag.business.viagem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.viagem.service.CalculadoraKmPercorrido.Ponto;
import com.marillia.milebag.core.geo.Coordenada;

class CalculadoraKmPercorridoTest {

    private static final Instant T0 = Instant.parse("2026-11-22T12:00:00Z");
    /** Graus de latitude em 100 m. */
    private static final double GRAU_POR_100M = 100 / 111_195.0;
    private static final BiFunction<Coordenada, Coordenada, OptionalDouble> SEM_ESTRADA = (a, b) -> OptionalDouble.empty();

    private static Ponto ponto(double metrosAoNorte, long segundos) {
        return new Ponto(-16.6 + metrosAoNorte / 100 * GRAU_POR_100M, -49.2, 10.0, T0.plusSeconds(segundos));
    }

    private static double km(List<Ponto> pontos, BiFunction<Coordenada, Coordenada, OptionalDouble> estrada) {
        return CalculadoraKmPercorrido.calcular(pontos, estrada).doubleValue();
    }

    @Test
    void somaOCaminhoPontoAPonto() {
        // 300 m a cada 15 s (72 km/h), 10 trechos = 3 km.
        List<Ponto> pontos = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            pontos.add(ponto(i * 300, i * 15L));
        }
        assertThat(km(pontos, SEM_ESTRADA)).isCloseTo(3.0, Offset.offset(0.01));
    }

    @Test
    void semPontosOuComUmSoEZero() {
        assertThat(CalculadoraKmPercorrido.calcular(List.of(), SEM_ESTRADA)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(CalculadoraKmPercorrido.calcular(List.of(ponto(0, 0)), SEM_ESTRADA)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void saltoImpossivelDoGpsEDescartado() {
        // 5 km em 15 s (1200 km/h) e a volta ao caminho: o ponto do salto nao conta.
        List<Ponto> pontos = List.of(ponto(0, 0), ponto(300, 15), ponto(5_300, 30), ponto(600, 45), ponto(900, 60));
        assertThat(km(pontos, SEM_ESTRADA)).isCloseTo(0.9, Offset.offset(0.01));
    }

    @Test
    void pontoImprecisoEDescartado() {
        Ponto impreciso = new Ponto(-16.6 + 30 * GRAU_POR_100M, -49.2, 500.0, T0.plusSeconds(15));
        List<Ponto> pontos = List.of(ponto(0, 0), impreciso, ponto(600, 30));
        assertThat(km(pontos, SEM_ESTRADA)).isCloseTo(0.6, Offset.offset(0.01));
    }

    @Test
    void oscilacaoParadoNaoSoma() {
        // Parado no semaforo: o GPS oscila 3 m para la e para ca.
        List<Ponto> pontos = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pontos.add(ponto(i % 2 == 0 ? 0 : 3, i * 15L));
        }
        assertThat(km(pontos, SEM_ESTRADA)).isLessThan(0.01);
    }

    @Test
    void lacunaGrandeECompletadaPelaEstrada() {
        // App escondido 10 min: 6 km em linha reta, 8 km pela estrada.
        List<Ponto> pontos = List.of(ponto(0, 0), ponto(6_000, 600));
        assertThat(km(pontos, (a, b) -> OptionalDouble.of(8_000))).isCloseTo(8.0, Offset.offset(0.001));
    }

    @Test
    void semMotorDeRotasALacunaValeEmLinhaReta() {
        List<Ponto> pontos = List.of(ponto(0, 0), ponto(6_000, 600));
        assertThat(km(pontos, SEM_ESTRADA)).isCloseTo(6.0, Offset.offset(0.01));
    }

    @Test
    void estradaMaisCurtaQueALinhaRetaNaoReduz() {
        List<Ponto> pontos = List.of(ponto(0, 0), ponto(6_000, 600));
        assertThat(km(pontos, (a, b) -> OptionalDouble.of(1_000))).isCloseTo(6.0, Offset.offset(0.01));
    }

    @Test
    void pontosForaDeOrdemSaoOrdenadosPelaHora() {
        List<Ponto> pontos = List.of(ponto(600, 30), ponto(0, 0), ponto(300, 15));
        assertThat(km(pontos, SEM_ESTRADA)).isCloseTo(0.6, Offset.offset(0.01));
    }

    @Test
    void primeiroPontoErradoNaoZeraOKm() {
        // O primeiro fix caiu 3 km longe; os seguintes andam juntos, 300 m a cada 15 s.
        List<Ponto> pontos = new ArrayList<>();
        pontos.add(ponto(-3_000, 0));
        for (int i = 1; i <= 10; i++) {
            pontos.add(ponto(i * 300, i * 15L));
        }
        assertThat(km(pontos, SEM_ESTRADA)).isGreaterThan(2.0);
    }

    @Test
    void oscilacaoDentroDaPrecisaoNaoSoma() {
        // Parado 1 h no aeroporto com precisao de 40 m: o GPS pula 30 m para la e para ca.
        List<Ponto> pontos = new ArrayList<>();
        for (int i = 0; i < 240; i++) {
            pontos.add(new Ponto(-16.6 + (i % 2 == 0 ? 0 : 30) / 100.0 * GRAU_POR_100M, -49.2, 40.0, T0.plusSeconds(i * 15L)));
        }
        assertThat(km(pontos, SEM_ESTRADA)).isLessThan(0.01);
    }
}

