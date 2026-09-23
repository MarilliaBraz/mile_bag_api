package com.marillia.milebag.business.rota.model;

import com.marillia.milebag.core.geo.Coordenada;

/**
 * {@code origemAtual}: posicao atual do motorista (GPS do dispositivo),
 * sempre enviada pelo cliente - nao ha, ainda, um endereco-base do tenant
 * cadastrado (business/configuracao, HU futura) para servir de alternativa
 * quando a viagem nao comecou.
 *
 * <p>{@code destinoManual}: coordenada informada quando a geocodificacao
 * automatica do endereco do BDO falha (FR-006, Edge Case do spec.md).</p>
 */
public record CalcularRotaRequest(Coordenada origemAtual, Coordenada destinoManual) {
}
