package com.marillia.milebag.core.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodifica o traçado que o Valhalla devolve em cada trecho da rota: polyline codificada com precisao de
 * 6 casas decimais (o Google Polyline Algorithm, mas com 1e6 no lugar de 1e5).
 */
public final class Polyline6 {

    private static final double PRECISAO = 1e6;

    private Polyline6() {
    }

    public static List<Coordenada> decodificar(String codificada) {
        List<Coordenada> pontos = new ArrayList<>();
        if (codificada == null || codificada.isEmpty()) {
            return pontos;
        }
        int indice = 0;
        long latitude = 0;
        long longitude = 0;
        while (indice < codificada.length()) {
            int[] lido = lerValor(codificada, indice);
            latitude += lido[0];
            indice = lido[1];
            lido = lerValor(codificada, indice);
            longitude += lido[0];
            indice = lido[1];
            pontos.add(new Coordenada(latitude / PRECISAO, longitude / PRECISAO));
        }
        return pontos;
    }

    /** @return {valor decodificado, proximo indice} */
    private static int[] lerValor(String texto, int inicio) {
        int resultado = 0;
        int deslocamento = 0;
        int indice = inicio;
        int byteLido;
        do {
            byteLido = texto.charAt(indice++) - 63;
            resultado |= (byteLido & 0x1f) << deslocamento;
            deslocamento += 5;
        } while (byteLido >= 0x20);
        return new int[] {(resultado & 1) != 0 ? ~(resultado >> 1) : (resultado >> 1), indice};
    }
}
