package com.marillia.milebag.core.geo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * Proximidade entre pontos e um traçado (lista de coordenadas). Serve para saber por quais pontos de interesse
 * (ex.: praças de pedágio) uma rota passa. Usa uma projecao plana local (equiretangular) em torno do ponto, que
 * para raios de dezenas/centenas de metros erra bem menos que a precisao das coordenadas cadastradas.
 */
public final class GeometriaRota {

    private static final double METROS_POR_GRAU_LATITUDE = 111_320.0;

    private GeometriaRota() {
    }

    /** Candidatos a no maximo {@code raioMetros} do traçado, na ordem em que foram informados. */
    public static <T> List<T> proximosDoTracado(
            List<Coordenada> tracado, List<T> candidatos, Function<T, Coordenada> localizacao, double raioMetros) {
        List<T> encontrados = new ArrayList<>();
        if (tracado == null || tracado.size() < 2) {
            return encontrados;
        }
        for (T candidato : candidatos) {
            Coordenada ponto = localizacao.apply(candidato);
            if (ponto != null && distanciaAoTracadoMetros(tracado, ponto) <= raioMetros) {
                encontrados.add(candidato);
            }
        }
        return encontrados;
    }

    /**
     * Traçado com menos pontos, sem se afastar mais de {@code toleranciaMetros} do original (Douglas-Peucker).
     * Para desenhar a rota no mapa: o traçado do motor de rotas tem um ponto a cada poucos metros.
     */
    public static List<Coordenada> simplificar(List<Coordenada> tracado, double toleranciaMetros) {
        if (tracado == null || tracado.size() < 3) {
            return tracado == null ? List.of() : List.copyOf(tracado);
        }
        boolean[] manter = new boolean[tracado.size()];
        manter[0] = true;
        manter[tracado.size() - 1] = true;
        // Pilha explicita em vez de recursao: rotas longas tem milhares de pontos.
        Deque<int[]> trechos = new ArrayDeque<>();
        trechos.push(new int[] {0, tracado.size() - 1});
        while (!trechos.isEmpty()) {
            int[] trecho = trechos.pop();
            int inicio = trecho[0];
            int fim = trecho[1];
            List<Coordenada> segmento = List.of(tracado.get(inicio), tracado.get(fim));
            int maisDistante = -1;
            double maiorDistancia = toleranciaMetros;
            for (int i = inicio + 1; i < fim; i++) {
                double distancia = distanciaAoTracadoMetros(segmento, tracado.get(i));
                if (distancia > maiorDistancia) {
                    maiorDistancia = distancia;
                    maisDistante = i;
                }
            }
            if (maisDistante >= 0) {
                manter[maisDistante] = true;
                trechos.push(new int[] {inicio, maisDistante});
                trechos.push(new int[] {maisDistante, fim});
            }
        }
        List<Coordenada> simplificado = new ArrayList<>();
        for (int i = 0; i < tracado.size(); i++) {
            if (manter[i]) {
                simplificado.add(tracado.get(i));
            }
        }
        return simplificado;
    }

    static double distanciaAoTracadoMetros(List<Coordenada> tracado, Coordenada ponto) {
        double metrosPorGrauLongitude = METROS_POR_GRAU_LATITUDE * Math.cos(Math.toRadians(ponto.latitude()));
        double menor = Double.MAX_VALUE;
        for (int i = 1; i < tracado.size(); i++) {
            double ax = (tracado.get(i - 1).longitude() - ponto.longitude()) * metrosPorGrauLongitude;
            double ay = (tracado.get(i - 1).latitude() - ponto.latitude()) * METROS_POR_GRAU_LATITUDE;
            double bx = (tracado.get(i).longitude() - ponto.longitude()) * metrosPorGrauLongitude;
            double by = (tracado.get(i).latitude() - ponto.latitude()) * METROS_POR_GRAU_LATITUDE;
            menor = Math.min(menor, distanciaDaOrigemAoSegmento(ax, ay, bx, by));
        }
        return menor;
    }

    /** Distancia da origem (o ponto de interesse, apos transladar) ao segmento A-B. */
    private static double distanciaDaOrigemAoSegmento(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double comprimentoAoQuadrado = dx * dx + dy * dy;
        double t = comprimentoAoQuadrado == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / comprimentoAoQuadrado));
        double px = ax + t * dx;
        double py = ay + t * dy;
        return Math.sqrt(px * px + py * py);
    }
}
