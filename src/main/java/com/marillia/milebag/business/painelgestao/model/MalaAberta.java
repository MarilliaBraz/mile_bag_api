package com.marillia.milebag.business.painelgestao.model;

import java.time.Instant;
import java.util.UUID;

/** BDO ainda nao entregue (nem cancelado, nem carona), com o prazo ja resolvido. {@code entregaId} nulo = ninguem assumiu. */
public record MalaAberta(
        UUID bdoId,
        UUID entregaId,
        String numero,
        String passageiro,
        String base,
        String motorista,
        String status,
        Instant prazo,
        Instant retiradaEm,
        Instant ultimaAtualizacao,
        long tentativas,
        String ultimoMotivo,
        boolean avariaRetirada) {
}
