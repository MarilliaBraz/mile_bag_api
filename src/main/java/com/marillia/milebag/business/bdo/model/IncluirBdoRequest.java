package com.marillia.milebag.business.bdo.model;

/** Corpo de {@code POST /api/v1/gestao/bdos}: os campos conferidos e, se a empresa colou, o telex original. */
public record IncluirBdoRequest(CamposExtraidosBdo campos, String textoOriginal) {
}
