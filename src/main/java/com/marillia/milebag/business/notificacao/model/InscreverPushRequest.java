package com.marillia.milebag.business.notificacao.model;

/** {@code PushSubscription.toJSON()} do navegador, com {@code keys} como {@code chaves} (contracts/notificacoes-api.md). */
public record InscreverPushRequest(String endpoint, Chaves chaves) {

    public record Chaves(String p256dh, String auth) {
    }
}
