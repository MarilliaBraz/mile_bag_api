package com.marillia.milebag.business.bagagemencontrada.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Ver specs/HU34-bagagem-com-etiqueta/contracts/bagagens-api.md. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BagagemEncontradaResponse(
        UUID id,
        TipoBagagemEncontrada tipo,
        SituacaoBagagemEncontrada situacao,
        Lido lido,
        Etiqueta etiqueta,
        Localizacao localizacao,
        AeroportoRegistro aeroporto,
        String registradoPor,
        Instant registradaEm,
        Instant criadoEm,
        String fotoUrl) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Lido(String numeroEtiqueta, String companhia, String destino, String voo, String nomePassageiro, OrigemLeitura origemLeitura) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Etiqueta(String numeroEtiqueta, String companhia, String destino, String voo, String nomePassageiro) {
    }

    public record Localizacao(Double latitude, Double longitude, OrigemLocalizacao origem) {
    }

    /** {@code distante}: mais de 50 km - a tela pede para confirmar o aeroporto. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AeroportoRegistro(String codigo, String nome, String cidade, String uf, Integer distanciaMetros, Boolean distante) {
    }
}
