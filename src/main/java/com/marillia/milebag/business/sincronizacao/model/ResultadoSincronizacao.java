package com.marillia.milebag.business.sincronizacao.model;

/** Ver contracts/sincronizacao-api.md. {@code NAO_ENCONTRADO} so aparece em GET /sync/status. */
public enum ResultadoSincronizacao {
    ACEITO,
    DUPLICADO,
    ERRO,
    NAO_ENCONTRADO
}
