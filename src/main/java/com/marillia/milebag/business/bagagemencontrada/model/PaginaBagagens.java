package com.marillia.milebag.business.bagagemencontrada.model;

import java.util.List;

/** Uma pagina da consulta de bagagens encontradas (HU36) - ver specs/HU36-consulta-bagagens/contracts/consulta-bagagens-api.md. */
public record PaginaBagagens(List<BagagemEncontradaResponse> itens, long total, int pagina, int tamanho) {
}
