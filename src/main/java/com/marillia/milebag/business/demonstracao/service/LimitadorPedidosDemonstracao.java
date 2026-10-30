package com.marillia.milebag.business.demonstracao.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.marillia.milebag.core.exception.LimiteDemonstracaoException;

/**
 * Limite de pedidos de demonstracao por IP (HU38): a pagina e publica, entao o formulario nao pode virar porta de spam.
 * Em memoria, como o {@code LimitadorTentativasLogin}: vale para uma unica instancia da API; com varias instancias o
 * contador deveria ir para um armazenamento compartilhado.
 */
@Component
public class LimitadorPedidosDemonstracao {

    static final int MAXIMO = 5;
    static final Duration JANELA = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> pedidos = new ConcurrentHashMap<>();
    private final Clock relogio;

    public LimitadorPedidosDemonstracao() {
        this(Clock.systemUTC());
    }

    LimitadorPedidosDemonstracao(Clock relogio) {
        this.relogio = relogio;
    }

    /** Conta mais um pedido do IP. @throws LimiteDemonstracaoException se ja houve {@value #MAXIMO} na ultima hora. */
    public void registrar(String ip) {
        Instant agora = relogio.instant();
        Instant inicioJanela = agora.minus(JANELA);
        pedidos.compute(ip, (chave, instantes) -> {
            Deque<Instant> recentes = instantes == null ? new ArrayDeque<>() : instantes;
            while (!recentes.isEmpty() && !recentes.peekFirst().isAfter(inicioJanela)) {
                recentes.pollFirst();
            }
            if (recentes.size() >= MAXIMO) {
                throw new LimiteDemonstracaoException();
            }
            recentes.addLast(agora);
            return recentes;
        });
    }
}
