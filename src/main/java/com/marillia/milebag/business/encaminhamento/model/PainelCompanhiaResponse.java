package com.marillia.milebag.business.encaminhamento.model;

/** Painel de atividades da companhia aerea: o que ainda pede acao. */
public record PainelCompanhiaResponse(long rascunhos, long semEncaminhamento, long emAndamento, long naoEntregues) {
}
