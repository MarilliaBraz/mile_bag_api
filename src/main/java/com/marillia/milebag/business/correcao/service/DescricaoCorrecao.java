package com.marillia.milebag.business.correcao.service;

/** Textos curtos de "antes" e "depois" das correcoes, iguais para a retirada e para a entrega (V51). */
public final class DescricaoCorrecao {

    private DescricaoCorrecao() {
    }

    public static String avarias(long fotos, String observacao) {
        String quantidade = fotos == 0 ? "sem fotos de avaria" : fotos == 1 ? "1 foto de avaria" : fotos + " fotos de avaria";
        return observacao == null || observacao.isBlank() ? quantidade : quantidade + " - " + observacao.strip();
    }

    public static String recebedor(String tipo, String nome) {
        return (tipo == null ? "?" : tipo) + " - " + (nome == null ? "?" : nome);
    }
}
