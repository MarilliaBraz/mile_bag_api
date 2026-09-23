package com.marillia.milebag.core.geo;

/**
 * Como uma {@link RotaAlternativa} foi escolhida (ver data-model.md da
 * HU03). Em {@code core} (nao em {@code business.rota}) pelo mesmo motivo de
 * {@link RotaAlternativa}: reaproveitado por rota, viagem e sincronizacao.
 */
public enum TipoSelecao {
    SUGERIDA,
    PROPRIA
}
