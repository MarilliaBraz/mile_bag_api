package com.marillia.milebag.core.geo;

/**
 * Ponto geografico (WGS84) usado como origem, destino ou ponto de tracado
 * manual. Em {@code core} (nao em {@code business.rota}) porque e
 * reaproveitado por mais de um dominio (rota, viagem, entrega.bagagemcarona)
 * sem carregar nenhuma regra de negocio propria.
 */
public record Coordenada(double latitude, double longitude) {
}
