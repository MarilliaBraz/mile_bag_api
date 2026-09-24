package com.marillia.milebag.business.pedagio.model;

/** Corpo de {@code PUT /pracas-pedagio/{id}/localizacao} (HU24): as duas coordenadas, ou as duas nulas para remover a localizacao. */
public record DefinirLocalizacaoRequest(Double latitude, Double longitude) {
}
