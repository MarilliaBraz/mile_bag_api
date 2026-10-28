package com.marillia.milebag.business.acompanhamento.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta publica do link "Acompanhe sua bagagem" (HU31) - ver contracts/acompanhamento-api.md. So o
 * necessario para o passageiro: nenhum dado do motorista alem da posicao, e a posicao so com a tentativa em
 * andamento (RN16).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AcompanhamentoResponse(
        Empresa empresa,
        String bdoNumero,
        EtapaAcompanhamento etapa,
        Instant iniciadaEm,
        Posicao posicao,
        Ponto destino,
        Janela janelaReagendamento) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Empresa(String nome, String logotipoUrl) {
    }

    public record Posicao(double latitude, double longitude, Instant registradaEm) {
    }

    public record Ponto(double latitude, double longitude) {
    }

    public record Janela(Instant inicio, Instant fim) {
    }
}
