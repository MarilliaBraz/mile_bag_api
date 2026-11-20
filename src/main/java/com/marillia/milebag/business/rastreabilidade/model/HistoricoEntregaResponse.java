package com.marillia.milebag.business.rastreabilidade.model;

import java.util.List;

/** A entrega e a linha do tempo da mala, do recebimento do BDO ate hoje, em ordem cronologica (V46). */
public record HistoricoEntregaResponse(EntregaGestaoResponse entrega, List<EventoRastreabilidade> eventos) {
}
