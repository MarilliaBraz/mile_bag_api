package com.marillia.milebag.business.tenant.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Limita quantos pedidos (ex.: e-mails de redefinicao de senha, HU25) cada chave pode fazer numa janela, para a
 * funcao publica nao servir de canhao de spam para o e-mail de ninguem. Em memoria (uma instancia).
 */
@Component
public class LimitadorSolicitacoes {

    static final int MAXIMO_POR_JANELA = 3;
    static final Duration JANELA = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> pedidos = new ConcurrentHashMap<>();

    /** @return {@code true} e registra o pedido, ou {@code false} se a chave ja atingiu o limite na janela. */
    public boolean permitir(String chave) {
        Deque<Instant> recentes = pedidos.computeIfAbsent(chave == null ? "" : chave.trim().toLowerCase(Locale.ROOT), k -> new ArrayDeque<>());
        synchronized (recentes) {
            Instant agora = Instant.now();
            while (!recentes.isEmpty() && recentes.peekFirst().isBefore(agora.minus(JANELA))) {
                recentes.pollFirst();
            }
            if (recentes.size() >= MAXIMO_POR_JANELA) {
                return false;
            }
            recentes.addLast(agora);
            return true;
        }
    }
}
