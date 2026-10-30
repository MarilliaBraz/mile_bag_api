package com.marillia.milebag.business.demonstracao.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.core.exception.LimiteDemonstracaoException;

class LimitadorPedidosDemonstracaoTest {

    /** Relogio que o teste avanca a mao. */
    private static final class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-10-30T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final RelogioManual relogio = new RelogioManual();
    private final LimitadorPedidosDemonstracao limitador = new LimitadorPedidosDemonstracao(relogio);

    @Test
    void cincoPedidosPassamOSextoEBloqueado() {
        for (int i = 0; i < 5; i++) {
            limitador.registrar("203.0.113.7");
        }

        assertThatThrownBy(() -> limitador.registrar("203.0.113.7")).isInstanceOf(LimiteDemonstracaoException.class);
    }

    @Test
    void outroIpNaoEAfetado() {
        for (int i = 0; i < 5; i++) {
            limitador.registrar("203.0.113.7");
        }

        assertThatCode(() -> limitador.registrar("198.51.100.2")).doesNotThrowAnyException();
    }

    @Test
    void janelaVencidaLibera() {
        for (int i = 0; i < 5; i++) {
            limitador.registrar("203.0.113.7");
        }
        relogio.avancar(Duration.ofMinutes(61));

        assertThatCode(() -> limitador.registrar("203.0.113.7")).doesNotThrowAnyException();
    }
}
