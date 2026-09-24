package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.util.UUID;

import com.marillia.milebag.core.geo.Coordenada;

/** Praca com localizacao e o valor vigente por passagem numa data (HU24) - o que o calculo de rota precisa para achar e valorar as pracas do traçado. */
public record PracaLocalizada(UUID id, String nome, Coordenada localizacao, BigDecimal valorVigente) {
}
