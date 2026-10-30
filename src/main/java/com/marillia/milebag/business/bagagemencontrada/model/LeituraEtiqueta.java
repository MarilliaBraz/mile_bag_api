package com.marillia.milebag.business.bagagemencontrada.model;

/** O que a foto da etiqueta revelou (HU34) - cada campo pode faltar; a conferencia completa e corrige. */
public record LeituraEtiqueta(
        String numeroEtiqueta, String companhia, String destino, String voo, String nomePassageiro, OrigemLeitura origem, String texto) {

    public static LeituraEtiqueta vazia(String texto) {
        return new LeituraEtiqueta(null, null, null, null, null, OrigemLeitura.NENHUMA, texto);
    }
}
