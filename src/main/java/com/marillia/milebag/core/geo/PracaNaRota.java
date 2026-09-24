package com.marillia.milebag.core.geo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Praca de pedagio por onde uma rota passa (HU24). So dados simples, sem regra de negocio (mesmo motivo de
 * {@link RotaAlternativa} viver em {@code core}). {@code valor} e o valor vigente por passagem no momento do
 * calculo - so para o motorista ver; o valor que conta para o reembolso e o congelado quando a viagem comeca.
 */
public record PracaNaRota(UUID pracaId, String nome, BigDecimal valor) {
}
