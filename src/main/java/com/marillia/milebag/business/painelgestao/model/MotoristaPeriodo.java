package com.marillia.milebag.business.painelgestao.model;

import java.time.Instant;
import java.util.UUID;

/** Motorista ativo com o ultimo acesso ao app, se esta em rota e as entregas dos ultimos 30 dias. */
public record MotoristaPeriodo(UUID id, String nome, String base, Instant ultimoAcesso, boolean emRota, long entregas, long noPrazo,
        long primeiraTentativa) {
}
