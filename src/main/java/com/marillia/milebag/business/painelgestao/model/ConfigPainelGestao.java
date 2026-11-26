package com.marillia.milebag.business.painelgestao.model;

/** Configuracao do painel de gestao, na leitura e na gravacao (V61; meta de OTD em %, V63 - nula na gravacao mantem a atual). */
public record ConfigPainelGestao(Integer prazoEntregaHoras, Integer horasSemAtualizacao, Integer metaOtd) {
}
