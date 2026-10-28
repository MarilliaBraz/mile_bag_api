package com.marillia.milebag.business.bagagemencontrada.model;

/** Conferencia da bagagem com etiqueta (HU34): numero da etiqueta e aeroporto obrigatorios. */
public record ConfirmarEtiquetaRequest(
        String numeroEtiqueta, String companhia, String destino, String voo, String nomePassageiro, String aeroportoCodigo) {
}
