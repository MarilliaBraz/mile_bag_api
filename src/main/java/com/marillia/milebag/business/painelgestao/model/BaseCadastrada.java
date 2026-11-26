package com.marillia.milebag.business.painelgestao.model;

/** Base da empresa com a posicao do aeroporto (nula se o codigo nao esta no cadastro de aeroportos). */
public record BaseCadastrada(String codigo, String nome, String cidade, Double latitude, Double longitude) {
}
