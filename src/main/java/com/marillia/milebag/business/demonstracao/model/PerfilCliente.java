package com.marillia.milebag.business.demonstracao.model;

/** Quem pede a demonstracao (HU38). */
public enum PerfilCliente {
    EMPRESA_ENTREGA("Empresa de entrega"),
    COMPANHIA_AEREA("Companhia aérea");

    private final String rotulo;

    PerfilCliente(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Com acento: vai no e-mail lido pela equipe. */
    public String rotulo() {
        return rotulo;
    }
}
