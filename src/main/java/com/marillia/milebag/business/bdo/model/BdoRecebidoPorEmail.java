package com.marillia.milebag.business.bdo.model;

import java.util.UUID;

/**
 * Evento de um BDO registrado automaticamente a partir do e-mail do tenant (RF21). Publicado por
 * {@code BdoService.criarAutomatico}; o aviso aos motoristas (HU32) escuta depois do commit, sem que o dominio
 * {@code bdo} dependa de {@code notificacao}.
 */
public record BdoRecebidoPorEmail(UUID bdoId, String numero) {
}
