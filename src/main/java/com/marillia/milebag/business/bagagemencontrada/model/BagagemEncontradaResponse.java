package com.marillia.milebag.business.bagagemencontrada.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Ver specs/HU34-bagagem-com-etiqueta/contracts/bagagens-api.md e specs/HU35-bagagem-sem-etiqueta/contracts/bagagens-sem-etiqueta-api.md. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BagagemEncontradaResponse(
        UUID id,
        TipoBagagemEncontrada tipo,
        SituacaoBagagemEncontrada situacao,
        Lido lido,
        Etiqueta etiqueta,
        Descricao descricao,
        Localizacao localizacao,
        AeroportoRegistro aeroporto,
        String registradoPor,
        Instant registradaEm,
        Instant criadoEm,
        String fotoUrl,
        String qrUrl,
        Encaminhamento encaminhamento) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Lido(String numeroEtiqueta, String companhia, String destino, String voo, String nomePassageiro, OrigemLeitura origemLeitura, CorBagagem cor) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Etiqueta(String numeroEtiqueta, String companhia, String destino, String voo, String nomePassageiro) {
    }

    /** Bagagem sem etiqueta registrada (HU35). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Descricao(
            TipoVolume tipoVolume, TamanhoBagagem tamanho, CorBagagem cor, List<SinalParticular> sinais, String marca, String observacoes,
            String codigoSid) {
    }

    public record Localizacao(Double latitude, Double longitude, OrigemLocalizacao origem) {
    }

    /** {@code distante}: mais de 50 km - a tela pede para confirmar o aeroporto. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AeroportoRegistro(String codigo, String nome, String cidade, String uf, Integer distanciaMetros, Boolean distante) {
    }

    /**
     * Encaminhamento ativo para entrega (HU37): so a etapa da entrega, sem o motorista (RD23). {@code etapa}:
     * AGUARDANDO_MOTORISTA, MOTORISTA_DESIGNADO, A_CAMINHO, ENTREGUE ou NAO_ENTREGUE.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Encaminhamento(
            String empresa, String numeroProcesso, String passageiroNome, String endereco, String etapa, String encaminhadoPor,
            Instant encaminhadoEm, Instant atualizadoEm) {
    }
}
