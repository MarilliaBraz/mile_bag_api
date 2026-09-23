package com.marillia.milebag.core.geo;

/**
 * Uma opcao de trajeto (FR-001/FR-002 da HU03). {@code possuiPedagio} e o
 * indicador agregado que o proprio motor de rotas (Valhalla) devolve para o
 * trajeto inteiro - o cruzamento fino com o cadastro de pracas de pedagio
 * por tenant (trecho a trecho) e responsabilidade do dominio
 * business/pedagio. Em {@code core} (nao em {@code business.rota}) porque e
 * reaproveitado por mais de um dominio (rota, viagem, entrega.bagagemcarona,
 * sincronizacao) sem carregar regra de negocio propria.
 */
public record RotaAlternativa(String id, double distanciaMetros, long duracaoSegundos, boolean possuiPedagio) {
}
