package com.marillia.milebag.avaliacaon2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.viagem.service.CalculadoraKmPercorrido;
import com.marillia.milebag.business.viagem.service.CalculadoraKmPercorrido.Ponto;
import com.marillia.milebag.core.geo.Coordenada;

/**
 * Avaliação N2 (Teste de Software, ADS 4): três casos unitários da regra do km efetivamente percorrido (item 9 do
 * Milebag). A classe testada é a do projeto, sem cópia: {@link CalculadoraKmPercorrido#calcular}.
 *
 * <p>Os pontos andam para o norte a partir de Goiânia. Um passo de 0,0027 grau de latitude mede cerca de 300 m
 * (raio da Terra de 6.371 km usado pela API), percorrido em 15 s, ou seja, 72 km/h.
 */
class CalculadoraKmPercorridoN2Test {

    private static final double LATITUDE_INICIAL = -16.6869;
    private static final double LONGITUDE = -49.2648;
    private static final double PASSO_GRAUS = 0.0027;
    private static final Instant INICIO = Instant.parse("2026-11-22T12:00:00Z");
    private static final double PRECISAO_BOA = 5.0;
    /** Tolerância das comparações em km: 2 m, bem abaixo de qualquer regra testada. */
    private static final double TOLERANCIA_KM = 0.002;

    /** Sem motor de rotas: nestes casos nenhum trecho passa de 500 m, então a estrada não é consultada. */
    private static final BiFunction<Coordenada, Coordenada, OptionalDouble> SEM_ESTRADA = (de, para) -> OptionalDouble.empty();

    private static Ponto ponto(double passosNorte, double passosLeste, Double precisao, int segundos) {
        return new Ponto(LATITUDE_INICIAL + passosNorte * PASSO_GRAUS, LONGITUDE + passosLeste * PASSO_GRAUS, precisao,
                INICIO.plusSeconds(segundos));
    }

    @Test
    @DisplayName("U-01 (esperado): três pontos bons em linha reta somam cerca de 0,600 km")
    void somaOsTrechosDeUmaViagemNormal() {
        // Arrange: A -> B -> C, 300 m e 15 s por trecho
        List<Ponto> pontos = List.of(
                ponto(0, 0, PRECISAO_BOA, 0),
                ponto(1, 0, PRECISAO_BOA, 15),
                ponto(2, 0, PRECISAO_BOA, 30));

        // Act
        BigDecimal km = CalculadoraKmPercorrido.calcular(pontos, SEM_ESTRADA);

        // Assert
        assertThat(km.doubleValue()).isCloseTo(0.600, within(TOLERANCIA_KM));
        assertThat(km.scale()).as("o km sai com 3 casas (metros)").isEqualTo(3);
    }

    @Test
    @DisplayName("U-02 (erro do GPS): ponto impreciso e salto impossível não entram no km")
    void descartaPontoImprecisoESaltoImpossivel() {
        // Arrange: a mesma viagem do U-01 com dois pontos ruins no meio
        List<Ponto> pontos = List.of(
                ponto(0, 0, PRECISAO_BOA, 0),
                ponto(1, 0, PRECISAO_BOA, 15),
                // precisão de 250 m (pior que o máximo de 100 m): descartado antes do cálculo
                ponto(1, 3, 250.0, 20),
                // 3 km a leste em 5 s (mais de 2.000 km/h): salto impossível, acima de 150 km/h
                ponto(1, 10, PRECISAO_BOA, 20),
                ponto(2, 0, PRECISAO_BOA, 30));

        // Act
        BigDecimal km = CalculadoraKmPercorrido.calcular(pontos, SEM_ESTRADA);

        // Assert: os pontos ruins não somam; o resultado é igual ao da viagem limpa
        assertThat(km.doubleValue()).isCloseTo(0.600, within(TOLERANCIA_KM));
    }

    @Test
    @DisplayName("U-03 (limite): precisão de exatamente 100 m conta; 100,1 m já é descartada")
    void precisaoNoLimiteDeCemMetros() {
        // Arrange: o ponto B fica cerca de 288 m a leste do caminho reto A -> C (o grau de longitude encolhe com a
        // latitude). Se B contar, o caminho passa por ele: 2 x 416 m = cerca de 0,831 km. Se B for descartado, vale a
        // reta A -> C (cerca de 0,600 km).
        Ponto a = ponto(0, 0, PRECISAO_BOA, 0);
        Ponto c = ponto(2, 0, PRECISAO_BOA, 30);
        List<Ponto> comBNoLimite = List.of(a, ponto(1, 1, 100.0, 15), c);
        List<Ponto> comBAcimaDoLimite = List.of(a, ponto(1, 1, 100.1, 15), c);

        // Act
        BigDecimal kmNoLimite = CalculadoraKmPercorrido.calcular(comBNoLimite, SEM_ESTRADA);
        BigDecimal kmAcimaDoLimite = CalculadoraKmPercorrido.calcular(comBAcimaDoLimite, SEM_ESTRADA);

        // Assert
        assertThat(CalculadoraKmPercorrido.PRECISAO_MAXIMA_METROS).isEqualTo(100.0);
        assertThat(kmNoLimite.doubleValue()).as("100 m: B entra").isCloseTo(0.831, within(TOLERANCIA_KM));
        assertThat(kmAcimaDoLimite.doubleValue()).as("100,1 m: B sai").isCloseTo(0.600, within(TOLERANCIA_KM));
    }
}
