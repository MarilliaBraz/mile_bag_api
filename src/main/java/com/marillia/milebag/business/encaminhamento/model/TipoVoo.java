package com.marillia.milebag.business.encaminhamento.model;

/** V64: o tipo do voo define o prazo da ANAC para restituir a bagagem (7 dias domestico, 21 internacional). */
public enum TipoVoo {
    DOMESTICO(7),
    INTERNACIONAL(21);

    private final int diasPrazoAnac;

    TipoVoo(int diasPrazoAnac) {
        this.diasPrazoAnac = diasPrazoAnac;
    }

    public int diasPrazoAnac() {
        return diasPrazoAnac;
    }
}
