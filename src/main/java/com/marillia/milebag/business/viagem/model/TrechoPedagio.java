package com.marillia.milebag.business.viagem.model;

/**
 * Sinalizacao de pedagio dentro de um {@code CalculoRota} (FR-010).
 *
 * <p>{@code valorCadastrado} e sempre {@code false} nesta HU: o cruzamento
 * com o cadastro de pracas de pedagio por tenant e responsabilidade do
 * dominio business/pedagio, de uma HU futura ainda nao implementada. O que
 * ha disponivel hoje e apenas o indicador agregado que o motor de rotas
 * (Valhalla, HU03) devolve para o trajeto inteiro - por isso a lista tem no
 * maximo um item, sem detalhe de trecho/valor especifico (nao inventado).
 */
public record TrechoPedagio(String descricao, boolean valorCadastrado) {
}
