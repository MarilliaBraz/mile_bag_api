package com.marillia.milebag.business.viagem.model;

/** HU31: {@code rastreando = false} quando o motorista nao tem viagem em andamento - o app para de enviar. */
public record RegistrarPosicaoResponse(boolean rastreando) {
}
