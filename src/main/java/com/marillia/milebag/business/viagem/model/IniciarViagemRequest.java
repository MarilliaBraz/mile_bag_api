package com.marillia.milebag.business.viagem.model;

/** {@code lat}/{@code lng} omitidos gravam o checkpoint de partida com {@code ausente = true}. */
public record IniciarViagemRequest(Double lat, Double lng) {
}
