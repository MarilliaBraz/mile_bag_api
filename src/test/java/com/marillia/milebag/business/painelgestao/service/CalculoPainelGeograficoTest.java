package com.marillia.milebag.business.painelgestao.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.painelgestao.model.BaseCadastrada;
import com.marillia.milebag.business.painelgestao.model.MalaAberta;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse.BaseOperacao;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse.SituacaoBase;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse.SituacaoRota;
import com.marillia.milebag.business.painelgestao.model.ViagemAtiva;

/** Fase 2: situacao e ranking das bases, rota longa, parada, sem sinal e desvio. */
class CalculoPainelGeograficoTest {

    private final Instant agora = Instant.parse("2026-11-26T18:00:00Z");
    private final Instant inicioHoje = Instant.parse("2026-11-26T03:00:00Z");

    private MalaAberta naBase(String base, Duration esperando, Duration paraOPrazo) {
        return new MalaAberta(UUID.randomUUID(), null, "BDO", "P", base, null, "AGUARDANDO_ATRIBUICAO", agora.plus(paraOPrazo), null,
                agora.minus(esperando), 0, null, false, agora.minus(esperando));
    }

    private ViagemAtiva viagem(Double metrosPrevistos, boolean pedagio, double metrosPercorridos, Duration semPosicao, long recentes,
            Double deslocamento) {
        return new ViagemAtiva(UUID.randomUUID(), UUID.randomUUID(), "BDO", "Motorista", "GYN", "Anápolis", "GO", agora.minus(Duration.ofHours(2)),
                metrosPrevistos, pedagio, pedagio ? 1 : 0, false, metrosPercorridos, agora.minus(semPosicao), -16.3, -48.9, recentes, deslocamento);
    }

    @Test
    void classificaBasesEOrdenaAsPiores() {
        List<BaseCadastrada> bases = List.of(new BaseCadastrada("GYN", "Goiânia", "Goiânia", -16.6, -49.2),
                new BaseCadastrada("BSB", "Brasília", "Brasília", -15.8, -47.9), new BaseCadastrada("CGH", "Congonhas", "São Paulo", -23.6, -46.6));
        List<MalaAberta> abertas = List.of(
                naBase("GYN", Duration.ofMinutes(30), Duration.ofHours(-1)),
                naBase("BSB", Duration.ofHours(3), Duration.ofHours(10)),
                naBase("BSB", Duration.ofHours(1), Duration.ofHours(10)));

        PainelGeograficoResponse painel = CalculoPainelGeografico.calcular(bases, abertas, List.of(), List.of(), agora, inicioHoje);

        assertThat(painel.bases()).extracting(BaseOperacao::codigo, BaseOperacao::situacao).containsExactly(
                org.assertj.core.groups.Tuple.tuple("GYN", SituacaoBase.CRITICO),
                org.assertj.core.groups.Tuple.tuple("BSB", SituacaoBase.RETENCAO),
                org.assertj.core.groups.Tuple.tuple("CGH", SituacaoBase.NORMAL));
        assertThat(painel.ranking()).extracting(BaseOperacao::codigo).containsExactly("BSB", "GYN");
        assertThat(painel.ranking().get(0).esperaMediaMinutos()).isEqualTo(120);
        assertThat(painel.ranking().get(0).maiorEsperaMinutos()).isEqualTo(180);
    }

    @Test
    void identificaRotaLongaParadaSemSinalEDesvio() {
        assertThat(CalculoPainelGeografico.rota(viagem(60_000.0, false, 20_000, Duration.ofMinutes(1), 5, 1_000.0), agora))
                .satisfies(r -> {
                    assertThat(r.longa()).isTrue();
                    assertThat(r.situacao()).isEqualTo(SituacaoRota.NORMAL);
                });
        assertThat(CalculoPainelGeografico.rota(viagem(10_000.0, true, 5_000, Duration.ofMinutes(1), 6, 120.0), agora).situacao())
                .isEqualTo(SituacaoRota.PARADO);
        assertThat(CalculoPainelGeografico.rota(viagem(10_000.0, false, 5_000, Duration.ofMinutes(45), 0, null), agora).situacao())
                .isEqualTo(SituacaoRota.SEM_SINAL);
        assertThat(CalculoPainelGeografico.rota(viagem(10_000.0, false, 20_000, Duration.ofMinutes(1), 5, 2_000.0), agora).situacao())
                .isEqualTo(SituacaoRota.DESVIO);
    }
}
