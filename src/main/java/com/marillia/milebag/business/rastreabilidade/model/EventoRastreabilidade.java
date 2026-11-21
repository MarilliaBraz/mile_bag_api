package com.marillia.milebag.business.rastreabilidade.model;

import java.time.Instant;

/**
 * Um acontecimento na vida da mala, para a linha do tempo da empresa (V46). {@code descricao} resume o fato (motivo,
 * quem recebeu); {@code detalhe} traz o texto livre que o motorista escreveu, quando houver. Em {@code CANCELADO}
 * (V47), {@code motoristaNome} e quem cancelou pela empresa e {@code detalhe} o complemento dela.
 */
public record EventoRastreabilidade(
        TipoEventoRastreabilidade tipo,
        Instant dataHora,
        String motoristaNome,
        String descricao,
        String detalhe) {

    public enum TipoEventoRastreabilidade {
        BDO_RECEBIDO,
        /** V48: retirada da bagagem no aeroporto; {@code detalhe} e a observacao das avarias. */
        BAGAGEM_RETIRADA,
        VIAGEM_INICIADA,
        OCORRENCIA,
        DISPENSA_SEM_BAGAGEM,
        DISPENSA_COM_BAGAGEM,
        ENTREGUE,
        CANCELADO,
        /** V50: segunda via do BDO enviada por e-mail; {@code descricao} e o endereco de destino. */
        SEGUNDA_VIA_ENVIADA,
        /** V51: correcao de etapa; {@code descricao} e a etapa (FOTO_PORTA...), {@code detalhe} o motivo, antes e depois. */
        CORRECAO
    }
}
