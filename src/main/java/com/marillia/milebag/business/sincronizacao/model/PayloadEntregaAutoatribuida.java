package com.marillia.milebag.business.sincronizacao.model;

import java.util.UUID;

/**
 * {@code bdoId} pode ser o id real (BDO ja sincronizado antes) ou o
 * {@code idLocal} de um evento {@code BDO_CAPTURADO} anterior no mesmo
 * lote, ainda nao confirmado pelo servidor - resolvido por
 * {@code SincronizacaoEventoDispatcher}.
 */
public record PayloadEntregaAutoatribuida(UUID bdoId) {
}
