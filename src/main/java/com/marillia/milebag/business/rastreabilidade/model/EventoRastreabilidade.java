package com.marillia.milebag.business.rastreabilidade.model;

import java.time.Instant;

/**
 * Um acontecimento na vida da mala, para a linha do tempo da empresa (V46). {@code descricao} resume o fato (motivo,
 * quem recebeu); {@code detalhe} traz o texto livre que o motorista escreveu, quando houver.
 */
public record EventoRastreabilidade(
        TipoEventoRastreabilidade tipo,
        Instant dataHora,
        String motoristaNome,
        String descricao,
        String detalhe) {

    public enum TipoEventoRastreabilidade {
        BDO_RECEBIDO,
        VIAGEM_INICIADA,
        OCORRENCIA,
        DISPENSA_SEM_BAGAGEM,
        DISPENSA_COM_BAGAGEM,
        ENTREGUE
    }
}
