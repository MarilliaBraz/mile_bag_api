package com.marillia.milebag.business.emergencia.model;

/** Posicao opcional (HU30): sem GPS o alerta e enviado do mesmo jeito; com, as duas coordenadas vem juntas. */
public record AcionarAlertaRequest(Double latitude, Double longitude) {
}
