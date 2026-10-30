package com.marillia.milebag.business.viagem.model;

/** HU31: posicao atual do motorista, enviada pelo app durante a viagem. */
public record RegistrarPosicaoRequest(Double latitude, Double longitude) {
}
