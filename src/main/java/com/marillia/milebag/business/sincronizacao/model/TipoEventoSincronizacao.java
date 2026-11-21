package com.marillia.milebag.business.sincronizacao.model;

/** Ver contracts/sincronizacao-api.md. */
public enum TipoEventoSincronizacao {
    /** Captura por foto do motorista - desativada (V49): o servidor devolve erro com o motivo. */
    BDO_CAPTURADO,
    ENTREGA_AUTOATRIBUIDA,
    ROTA_ESCOLHIDA,
    VIAGEM_INICIADA,
    BAGAGEM_CARONA_VINCULADA,
    COMPROVANTE_REGISTRADO,
    OCORRENCIA_REGISTRADA,
    /** V48: retirada da bagagem no aeroporto (foto e avarias), antes de iniciar a viagem. */
    RETIRADA_REGISTRADA
}
