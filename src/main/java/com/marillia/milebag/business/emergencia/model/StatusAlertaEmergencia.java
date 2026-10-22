package com.marillia.milebag.business.emergencia.model;

/** Situacao do alerta de emergencia (RD16) - so sai de {@code ABERTO}, uma vez. */
public enum StatusAlertaEmergencia {
    ABERTO,
    CANCELADO,
    ATENDIDO
}
