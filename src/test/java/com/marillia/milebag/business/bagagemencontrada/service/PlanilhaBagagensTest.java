package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLeitura;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLocalizacao;
import com.marillia.milebag.business.bagagemencontrada.model.SinalParticular;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TamanhoBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.TipoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TipoVolume;

class PlanilhaBagagensTest {

    /** Registrada as 14:05 de Brasilia, fotografada as 14:03. */
    static BagagemEncontradaResponse comEtiqueta() {
        return new BagagemEncontradaResponse(
                UUID.randomUUID(), TipoBagagemEncontrada.COM_ETIQUETA, SituacaoBagagemEncontrada.REGISTRADA,
                new BagagemEncontradaResponse.Lido("0957123456", "LA", "GYN", "LA3456", "SILVA/MARIA", OrigemLeitura.CODIGO_BARRAS, null),
                new BagagemEncontradaResponse.Etiqueta("0957123456", "LA", "GYN", "LA3456", "SILVA/MARIA"), null,
                new BagagemEncontradaResponse.Localizacao(-23.4324, -46.4687, OrigemLocalizacao.FOTO),
                new BagagemEncontradaResponse.AeroportoRegistro("GRU", "Guarulhos", "Guarulhos", "SP", 192, false), "Ana Souza",
                Instant.parse("2026-10-29T17:05:00Z"), Instant.parse("2026-10-29T17:03:00Z"), "/foto", null,
                new BagagemEncontradaResponse.Encaminhamento(
                        "Entregas Rápidas", "GRUZZ12345", "SILVA/MARIA", "Rua das Flores, 100", "A_CAMINHO", "Ana Souza",
                        Instant.parse("2026-10-29T18:00:00Z"), Instant.parse("2026-10-29T19:00:00Z")));
    }

    static BagagemEncontradaResponse semEtiqueta(String observacoes) {
        return new BagagemEncontradaResponse(
                UUID.randomUUID(), TipoBagagemEncontrada.SEM_ETIQUETA, SituacaoBagagemEncontrada.REGISTRADA,
                new BagagemEncontradaResponse.Lido(null, null, null, null, null, OrigemLeitura.NENHUMA, CorBagagem.VERMELHA), null,
                new BagagemEncontradaResponse.Descricao(
                        TipoVolume.MALA_RIGIDA, TamanhoBagagem.M, CorBagagem.VERMELHA, List.of(SinalParticular.FITA, SinalParticular.CADEADO), "Samsonite",
                        observacoes, "SID-GRU-2026-000002"),
                new BagagemEncontradaResponse.Localizacao(null, null, OrigemLocalizacao.NENHUMA),
                new BagagemEncontradaResponse.AeroportoRegistro("GRU", "Guarulhos", "Guarulhos", "SP", null, null), "Ana Souza",
                Instant.parse("2026-10-29T17:05:00Z"), Instant.parse("2026-10-29T17:03:00Z"), "/foto", "/qr", null);
    }

    @Test
    void planilhaParaOExcelEmPortugues() {
        String csv = new String(PlanilhaBagagens.gerar(List.of(comEtiqueta(), semEtiqueta("Fita verde; na alça"))), StandardCharsets.UTF_8);
        String[] linhas = csv.split("\r\n");

        assertThat(csv).startsWith("﻿Tipo;Situação;Número da etiqueta;Código SID;");
        assertThat(linhas).hasSize(3);
        assertThat(linhas[1]).isEqualTo("Com etiqueta;Registrada;0957123456;;LA;GYN;LA3456;SILVA/MARIA;;;;;;;GRU;192 m;-23,432400;-46,468700;"
                + "GPS da foto;29/10/2026 14:05;29/10/2026 14:03;Ana Souza;Entregas Rápidas;GRUZZ12345;A caminho");
        assertThat(linhas[2]).isEqualTo("Sem etiqueta;Registrada;;SID-GRU-2026-000002;;;;;Mala rígida;M (até 70 cm);Vermelha;Fitas, Cadeado;"
                + "Samsonite;\"Fita verde; na alça\";GRU;;;;Sem localização;29/10/2026 14:05;29/10/2026 14:03;Ana Souza;;;");
    }

    @Test
    void textoDigitadoNaoViraFormula() {
        assertThat(PlanilhaBagagens.celula("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(PlanilhaBagagens.celula("+5511999")).isEqualTo("'+5511999");
        assertThat(PlanilhaBagagens.celula("@soma")).isEqualTo("'@soma");
        assertThat(PlanilhaBagagens.celula("-23,432400")).isEqualTo("-23,432400");
        assertThat(PlanilhaBagagens.celula("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
    }
}
