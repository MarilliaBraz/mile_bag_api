package com.marillia.milebag.business.acompanhamento.model;

/** Etapa mostrada no link de acompanhamento (HU31, RI19) - as duas ultimas encerram a tentativa e escondem a posicao. */
public enum EtapaAcompanhamento {
    VIAGEM_INICIADA,
    A_CAMINHO,
    CHEGADA,
    ENTREGUE,
    NAO_ENTREGUE,
    /**
     * V46: o motorista desta viagem dispensou a entrega e ela foi passada para outro. Este link termina aqui; o
     * passageiro recebe um link novo quando o proximo motorista sair (um link por viagem).
     */
    TRANSFERIDA;

    public boolean encerrada() {
        return this == ENTREGUE || this == NAO_ENTREGUE || this == TRANSFERIDA;
    }
}
