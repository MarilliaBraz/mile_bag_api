package com.marillia.milebag.business.configuracao.model;

/** Corpo comum de POST para base aeroportuaria e companhia aerea (mesmo formato nos dois endpoints do contrato). */
public record CodigoNomeRequest(String codigoIata, String nome) {
}
