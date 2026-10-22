package com.marillia.milebag.business.entrega.model;

import java.time.Instant;

import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Faixa de data e horario combinada com o passageiro para a nova tentativa
 * (RF22/RD07, HU29). Fica em {@code entrega} (e nao em {@code ocorrencia})
 * porque a entrega guarda a janela vigente e {@code ocorrencia} ja depende
 * de {@code entrega}, nunca o contrario.
 */
public record JanelaReagendamento(Instant inicio, Instant fim) {

    public JanelaReagendamento {
        if (inicio == null || fim == null) {
            throw new RegraNegocioException("Informe o inicio e o fim da janela de reagendamento.");
        }
        if (!fim.isAfter(inicio)) {
            throw new RegraNegocioException("O fim da janela de reagendamento deve ser depois do inicio.");
        }
    }

    /** Nulo quando nenhum dos dois foi informado; incompleta ou invertida lanca {@link RegraNegocioException}. */
    public static JanelaReagendamento deCampos(Instant inicio, Instant fim) {
        if (inicio == null && fim == null) {
            return null;
        }
        return new JanelaReagendamento(inicio, fim);
    }
}
