package com.marillia.milebag.business.bagagemencontrada.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uma bagagem achada pela busca nacional (HU36, RD22): so o que qualquer companhia pode ver. {@code id} so vem quando o
 * registro e da propria companhia, para abrir a ficha. Ver specs/HU36-consulta-bagagens/contracts/consulta-bagagens-api.md.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuscaNacionalResponse(
        Companhia companhia, Aeroporto aeroporto, Instant registradaEm, Double latitude, Double longitude, boolean daMinhaCompanhia, UUID id) {

    public record Companhia(String nome, String codigoIata) {
    }

    public record Aeroporto(String codigo, String nome, String cidade, String uf) {
    }
}
