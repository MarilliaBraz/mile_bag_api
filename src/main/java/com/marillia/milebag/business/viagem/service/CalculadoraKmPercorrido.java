package com.marillia.milebag.business.viagem.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.GeometriaRota;

/**
 * Item 9: km efetivamente percorrido a partir dos pontos de GPS gravados pelo app durante a viagem.
 *
 * <ul>
 *   <li>ponto com precisao pior que {@value #PRECISAO_MAXIMA_METROS} m nao conta;</li>
 *   <li>salto impossivel (acima de {@value #VELOCIDADE_MAXIMA_KMH} km/h desde o ultimo ponto aceito) nao conta;</li>
 *   <li>deslocamento menor que {@value #MOVIMENTO_MINIMO_METROS} m, ou que a soma das precisoes dos dois pontos (oscilacao
 *       do GPS parado), nao soma - e o trecho nao se perde: ele entra quando o motorista de fato se afasta;</li>
 *   <li>{@value #SALTOS_PARA_REANCORAR} saltos seguidos: o ponto de referencia era o errado (primeiro fix ruim) e o calculo
 *       recomeca do ultimo deles;</li>
 *   <li>trecho sem pontos maior que {@value #LACUNA_PELA_ESTRADA_METROS} m (app escondido, sem sinal) e completado pela
 *       estrada; sem o motor de rotas, ou se a estrada der menos que a linha reta, vale a linha reta.</li>
 * </ul>
 */
public final class CalculadoraKmPercorrido {

    public static final double PRECISAO_MAXIMA_METROS = 100;
    public static final double VELOCIDADE_MAXIMA_KMH = 150;
    public static final double MOVIMENTO_MINIMO_METROS = 10;
    public static final double LACUNA_PELA_ESTRADA_METROS = 500;
    public static final int SALTOS_PARA_REANCORAR = 3;

    /** Um ponto gravado pelo app: posicao, precisao informada pelo GPS (pode faltar) e hora no celular. */
    public record Ponto(double latitude, double longitude, Double precisaoMetros, Instant dataHora) {
    }

    private CalculadoraKmPercorrido() {
    }

    /** {@code distanciaPelaEstrada}: metros entre dois pontos pela estrada, ou vazio se o motor de rotas nao responder. */
    public static BigDecimal calcular(List<Ponto> pontos, BiFunction<Coordenada, Coordenada, OptionalDouble> distanciaPelaEstrada) {
        List<Ponto> validos = pontos.stream()
                .filter(p -> p.precisaoMetros() == null || p.precisaoMetros() <= PRECISAO_MAXIMA_METROS)
                .sorted(Comparator.comparing(Ponto::dataHora))
                .toList();
        double metros = 0;
        Ponto ultimo = null;
        int saltosSeguidos = 0;
        for (Ponto ponto : validos) {
            if (ultimo == null) {
                ultimo = ponto;
                continue;
            }
            double reta = GeometriaRota.distanciaMetros(ultimo.latitude(), ultimo.longitude(), ponto.latitude(), ponto.longitude());
            if (reta < movimentoMinimo(ultimo, ponto)) {
                continue;
            }
            if (saltoImpossivel(ultimo, ponto, reta)) {
                if (++saltosSeguidos >= SALTOS_PARA_REANCORAR) {
                    ultimo = ponto;
                    saltosSeguidos = 0;
                }
                continue;
            }
            saltosSeguidos = 0;
            metros += reta > LACUNA_PELA_ESTRADA_METROS ? pelaEstrada(ultimo, ponto, reta, distanciaPelaEstrada) : reta;
            ultimo = ponto;
        }
        return BigDecimal.valueOf(metros / 1000).setScale(3, RoundingMode.HALF_UP);
    }

    private static double movimentoMinimo(Ponto de, Ponto para) {
        double precisoes = (de.precisaoMetros() == null ? 0 : de.precisaoMetros()) + (para.precisaoMetros() == null ? 0 : para.precisaoMetros());
        return Math.max(MOVIMENTO_MINIMO_METROS, precisoes);
    }

    private static boolean saltoImpossivel(Ponto de, Ponto para, double metros) {
        double segundos = Duration.between(de.dataHora(), para.dataHora()).toMillis() / 1000.0;
        return segundos <= 0 || metros / segundos * 3.6 > VELOCIDADE_MAXIMA_KMH;
    }

    private static double pelaEstrada(Ponto de, Ponto para, double reta,
            BiFunction<Coordenada, Coordenada, OptionalDouble> distanciaPelaEstrada) {
        OptionalDouble estrada = distanciaPelaEstrada.apply(
                new Coordenada(de.latitude(), de.longitude()), new Coordenada(para.latitude(), para.longitude()));
        return estrada.isPresent() && estrada.getAsDouble() > reta ? estrada.getAsDouble() : reta;
    }
}
