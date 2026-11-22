package com.marillia.milebag.business.rastreabilidade.model;

import java.util.List;

/** A entrega e a linha do tempo da mala, do recebimento do BDO ate hoje, em ordem cronologica (V46). */
/** Item 9: {@code quilometragem}, o km de cada viagem encerrada da entrega (auditoria do calculado x percorrido). */
public record HistoricoEntregaResponse(EntregaGestaoResponse entrega, List<EventoRastreabilidade> eventos, List<KmViagemResponse> quilometragem) {
}
