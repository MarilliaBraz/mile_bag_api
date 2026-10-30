package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TipoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TipoVolume;
import com.marillia.milebag.core.exception.RegraNegocioException;

class ConsultaBagagensTest {

    @Test
    void semFiltrosConsultaAsRegistradas() {
        ConsultaBagagens consulta = ConsultaBagagens.de(null, "", " ", null, null, null, null, "  ");

        assertThat(consulta).isEqualTo(new ConsultaBagagens(null, null, null, null, null, null, SituacaoBagagemEncontrada.REGISTRADA, null));
        assertThat(consulta.ordem().getOrderFor("registradaEm")).isNotNull();
    }

    @Test
    void leOsFiltrosSemDiferenciarMaiusculas() {
        ConsultaBagagens consulta = ConsultaBagagens.de("gru", "2026-10-01", "2026-10-29", "sem_etiqueta", "mochila", "azul", "rascunho", " SID-GRU ");

        assertThat(consulta).isEqualTo(new ConsultaBagagens(
                "GRU", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 29), TipoBagagemEncontrada.SEM_ETIQUETA, TipoVolume.MOCHILA, CorBagagem.AZUL,
                SituacaoBagagemEncontrada.RASCUNHO, "SID-GRU"));
        // O rascunho ainda nao tem data de registro: ordena e filtra pela da foto.
        assertThat(consulta.ordem().getOrderFor("criadoEm")).isNotNull();
    }

    @Test
    void recusaValoresForaDasOpcoes() {
        assertThatThrownBy(() -> ConsultaBagagens.de("GRUU", null, null, null, null, null, null, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> ConsultaBagagens.de(null, "29/10/2026", null, null, null, null, null, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> ConsultaBagagens.de(null, "2026-10-29", "2026-10-01", null, null, null, null, null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("data inicial");
        assertThatThrownBy(() -> ConsultaBagagens.de(null, null, null, "PERDIDA", null, null, null, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> ConsultaBagagens.de(null, null, null, null, "BAU", null, null, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> ConsultaBagagens.de(null, null, null, null, null, "DOURADA", null, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> ConsultaBagagens.de(null, null, null, null, null, null, "APAGADA", null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> ConsultaBagagens.de(null, null, null, null, null, null, null, "x".repeat(61))).isInstanceOf(RegraNegocioException.class);
    }
}
