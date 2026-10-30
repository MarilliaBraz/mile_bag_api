package com.marillia.milebag.business.acompanhamento.model;

/** Etapa mostrada no link de acompanhamento (HU31, RI19) - as duas ultimas encerram a tentativa e escondem a posicao. */
public enum EtapaAcompanhamento {
    VIAGEM_INICIADA,
    A_CAMINHO,
    CHEGADA,
    ENTREGUE,
    NAO_ENTREGUE;

    public boolean encerrada() {
        return this == ENTREGUE || this == NAO_ENTREGUE;
    }
}
