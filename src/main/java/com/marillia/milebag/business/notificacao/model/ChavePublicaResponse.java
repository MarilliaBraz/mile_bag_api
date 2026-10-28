package com.marillia.milebag.business.notificacao.model;

/** Chave publica VAPID, usada pelo navegador em {@code pushManager.subscribe} (HU32). */
public record ChavePublicaResponse(String chavePublica) {
}
