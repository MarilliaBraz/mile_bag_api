package com.marillia.milebag.business.acompanhamento.model;

/** Pesquisa do passageiro: nota de 0 a 10 (obrigatoria), cordialidade de 1 a 5, estado da mala e comentario. */
public record AvaliarEntregaRequest(Integer nota, Integer cordialidade, Boolean malaEmBomEstado, String comentario) {
}
