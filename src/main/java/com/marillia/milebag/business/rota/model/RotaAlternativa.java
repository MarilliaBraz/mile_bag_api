package com.marillia.milebag.business.rota.model;

/**
 * Uma opcao de trajeto (FR-001/FR-002). {@code possuiPedagio} e o indicador
 * agregado que o proprio motor de rotas (Valhalla) devolve para o trajeto
 * inteiro - o cruzamento fino com o cadastro de pracas de pedagio por
 * tenant (trecho a trecho) e responsabilidade do dominio business/pedagio,
 * de uma HU futura ainda nao implementada.
 */
public record RotaAlternativa(String id, double distanciaMetros, long duracaoSegundos, boolean possuiPedagio) {
}
