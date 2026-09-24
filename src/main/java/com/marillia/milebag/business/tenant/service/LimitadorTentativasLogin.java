package com.marillia.milebag.business.tenant.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.marillia.milebag.core.exception.LoginBloqueadoException;

/**
 * Bloqueio temporario de um login apos varias senhas erradas seguidas (HU22).
 * Em memoria: vale para uma unica instancia da API; com varias instancias o
 * contador deveria ir para um armazenamento compartilhado.
 */
@Component
public class LimitadorTentativasLogin {

    static final int MAXIMO_FALHAS = 5;
    static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private record Estado(int falhas, Instant bloqueadoAte) {
    }

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();

    /** @throws LoginBloqueadoException se o login ainda estiver bloqueado. */
    public void verificar(String login) {
        Estado estado = estados.get(chave(login));
        if (estado != null && estado.bloqueadoAte() != null) {
            if (Instant.now().isBefore(estado.bloqueadoAte())) {
                long minutos = Math.max(1, Duration.between(Instant.now(), estado.bloqueadoAte()).toMinutes() + 1);
                throw new LoginBloqueadoException(minutos);
            }
            estados.remove(chave(login));
        }
    }

    public void registrarFalha(String login) {
        estados.merge(chave(login), new Estado(1, null), (atual, novo) -> {
            int falhas = atual.falhas() + 1;
            return new Estado(falhas, falhas >= MAXIMO_FALHAS ? Instant.now().plus(BLOQUEIO) : null);
        });
    }

    public void registrarSucesso(String login) {
        estados.remove(chave(login));
    }

    private String chave(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}
