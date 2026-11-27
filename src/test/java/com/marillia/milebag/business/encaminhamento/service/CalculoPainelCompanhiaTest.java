package com.marillia.milebag.business.encaminhamento.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaGestaoResponse;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaGestaoResponse.ItemAgir;

/** Painel da companhia: semaforo da ANAC, custodia, tempos de localizacao x entrega, resolucao em 24 h e o que agir. */
class CalculoPainelCompanhiaTest {

    private final Instant agora = Instant.parse("2026-11-26T18:00:00Z");
    private final Instant inicioHoje = Instant.parse("2026-11-26T03:00:00Z");

    private CalculoPainelCompanhia.Encaminhamento aberto(String processo, EtapaEntrega etapa, Duration paraOPrazo) {
        Instant prazo = agora.plus(paraOPrazo);
        return new CalculoPainelCompanhia.Encaminhamento(UUID.randomUUID(), processo, "P", etapa, prazo.minus(Duration.ofDays(7)), prazo,
                agora.minus(Duration.ofHours(5)), null, null, etapa == EtapaEntrega.NAO_ENTREGUE ? agora.minus(Duration.ofHours(1)) : null, "GYN",
                agora.minus(Duration.ofHours(6)), "Entregas GO");
    }

    private CalculoPainelCompanhia.Encaminhamento entregue(String processo, Duration localizacao, Duration entrega, Instant entregueEm) {
        Instant encaminhado = entregueEm.minus(entrega);
        Instant registrada = encaminhado.minus(Duration.ofHours(1));
        Instant aberto = registrada.minus(localizacao);
        return new CalculoPainelCompanhia.Encaminhamento(UUID.randomUUID(), processo, "P", EtapaEntrega.ENTREGUE, aberto,
                aberto.plus(Duration.ofDays(7)), encaminhado, encaminhado.plus(Duration.ofHours(1)), entregueEm, null, "BSB", registrada,
                "Entregas DF");
    }

    @Test
    void calculaSemaforoCustodiaTemposEAgirAgora() {
        List<CalculoPainelCompanhia.Encaminhamento> encaminhamentos = List.of(
                aberto("NO-PRAZO", EtapaEntrega.AGUARDANDO_MOTORISTA, Duration.ofDays(4)),
                aberto("ATENCAO", EtapaEntrega.A_CAMINHO, Duration.ofDays(1)),
                aberto("ESTOURADO", EtapaEntrega.MOTORISTA_DESIGNADO, Duration.ofHours(-3)),
                aberto("NAO-ENTREGUE", EtapaEntrega.NAO_ENTREGUE, Duration.ofDays(5)),
                entregue("RAPIDA", Duration.ofHours(4), Duration.ofHours(3), agora.minus(Duration.ofHours(2))),
                entregue("LENTA", Duration.ofHours(30), Duration.ofHours(9), inicioHoje.minus(Duration.ofDays(3))));
        List<CalculoPainelCompanhia.NaSala> naSala = List.of(
                new CalculoPainelCompanhia.NaSala(UUID.randomUUID(), "GYN", agora.minus(Duration.ofHours(30)), "G31234", "Ana"),
                new CalculoPainelCompanhia.NaSala(UUID.randomUUID(), "GYN", agora.minus(Duration.ofHours(2)), "G31235", "Bia"));

        PainelCompanhiaGestaoResponse painel = CalculoPainelCompanhia.calcular(encaminhamentos, naSala, agora, inicioHoje);

        assertThat(painel.regulatorio()).isEqualTo(new PainelCompanhiaGestaoResponse.Regulatorio(2, 1, 1));
        assertThat(painel.custodia()).isEqualTo(new PainelCompanhiaGestaoResponse.Custodia(2, 2, 1, 1, 1));
        // Localizacao: (4 h + 30 h) / 2 = 17 h; entrega: (3 h + 9 h) / 2 = 6 h; em 24 h: so a RAPIDA (4 + 1 + 3 = 8 h).
        assertThat(painel.tempos()).isEqualTo(new PainelCompanhiaGestaoResponse.Tempos(17 * 60L, 6 * 60L, 50.0, 2));
        assertThat(painel.agirAgora()).extracting(ItemAgir::motivo, ItemAgir::processo).containsExactly(
                org.assertj.core.groups.Tuple.tuple("ESTOURADO", "ESTOURADO"),
                org.assertj.core.groups.Tuple.tuple("NAO_ENTREGUE", "NAO-ENTREGUE"),
                org.assertj.core.groups.Tuple.tuple("ATENCAO", "ATENCAO"),
                org.assertj.core.groups.Tuple.tuple("PARADA_NA_SALA", "G31234"));
        assertThat(painel.aeroportos().get(0).codigo()).isEqualTo("GYN");
        assertThat(painel.aeroportos().get(0).estourados()).isEqualTo(1);
        // SLA: em BSB a Entregas DF entregou as duas (coleta 1 h, entrega 6 h, 50% em 24 h); em GYN, a Entregas GO nada ainda.
        PainelCompanhiaGestaoResponse.Parceiro df = painel.parceiros().stream().filter(p -> p.empresa().equals("Entregas DF")).findFirst().orElseThrow();
        assertThat(df).isEqualTo(new PainelCompanhiaGestaoResponse.Parceiro("Entregas DF", "BSB", 2, 60L, 360L, 50.0, 0, 0));
    }

    @Test
    void calculaCustosPorProcessoEOnps() {
        CalculoPainelCompanhia.Encaminhamento rapida = entregue("RAPIDA", Duration.ofHours(2), Duration.ofHours(3), agora.minus(Duration.ofHours(1)));
        CalculoPainelCompanhia.Encaminhamento lenta = entregue("LENTA", Duration.ofHours(30), Duration.ofHours(9), agora.minus(Duration.ofDays(2)));
        List<CalculoPainelCompanhia.Custo> custos = List.of(
                new CalculoPainelCompanhia.Custo(rapida.bagagemId(), com.marillia.milebag.business.encaminhamento.model.TipoCusto.FRETE,
                        new java.math.BigDecimal("80.00")),
                new CalculoPainelCompanhia.Custo(lenta.bagagemId(), com.marillia.milebag.business.encaminhamento.model.TipoCusto.FRETE,
                        new java.math.BigDecimal("80.00")),
                new CalculoPainelCompanhia.Custo(lenta.bagagemId(), com.marillia.milebag.business.encaminhamento.model.TipoCusto.REEMBOLSO_EMERGENCIAL,
                        new java.math.BigDecimal("250.00")));
        List<CalculoPainelCompanhia.Avaliacao> avaliacoes = List.of(
                new CalculoPainelCompanhia.Avaliacao(10, 5, true), new CalculoPainelCompanhia.Avaliacao(9, 4, true),
                new CalculoPainelCompanhia.Avaliacao(5, 2, false), new CalculoPainelCompanhia.Avaliacao(8, null, null));

        PainelCompanhiaGestaoResponse painel = CalculoPainelCompanhia.calcular(List.of(rapida, lenta), List.of(), custos, avaliacoes, agora, inicioHoje);

        // Total 410; 2 processos = 205 cada; a resolvida em 24 h custou 80 e a lenta 330.
        assertThat(painel.financeiro().total()).isEqualByComparingTo("410.00");
        assertThat(painel.financeiro().porProcesso()).isEqualByComparingTo("205.00");
        assertThat(painel.financeiro().porProcessoAte24h()).isEqualByComparingTo("80.00");
        assertThat(painel.financeiro().porProcessoAcima24h()).isEqualByComparingTo("330.00");
        // NPS: 2 promotores e 1 detrator em 4 = 25.
        assertThat(painel.satisfacao()).isEqualTo(new PainelCompanhiaGestaoResponse.Satisfacao(4, 25.0, 8.0, 3.7, 1));
    }
}
