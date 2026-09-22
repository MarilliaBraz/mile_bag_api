package com.marillia.milebag.business.sincronizacao.model;

import java.util.List;

public record SincronizarLoteRequest(List<EventoSincronizacaoRequest> eventos) {
}
