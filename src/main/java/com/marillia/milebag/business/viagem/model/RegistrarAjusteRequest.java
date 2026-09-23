package com.marillia.milebag.business.viagem.model;

/** Request de {@code POST /api/v1/motorista/viagens/{viagemId}/ajustes} (HU17). */
public record RegistrarAjusteRequest(CampoAjusteViagem campo, String valorNovo, String justificativa) {
}
