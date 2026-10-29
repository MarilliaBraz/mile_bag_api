package com.marillia.milebag.business.encaminhamento.model;

/**
 * Etapa da entrega de uma bagagem encaminhada (HU37, RD23), mantida pelos triggers da V45 a partir da entrega do BDO
 * na empresa. E tudo o que a companhia ve da entrega.
 */
public enum EtapaEntrega {
    AGUARDANDO_MOTORISTA,
    MOTORISTA_DESIGNADO,
    A_CAMINHO,
    ENTREGUE,
    NAO_ENTREGUE,
    CANCELADO
}
