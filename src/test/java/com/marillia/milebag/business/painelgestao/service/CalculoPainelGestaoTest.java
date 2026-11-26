package com.marillia.milebag.business.painelgestao.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.painelgestao.model.MalaAberta;
import com.marillia.milebag.business.painelgestao.model.MalaEntregue;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoResponse;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoResponse.ItemMala;

/** Painel de gestao (fase 1): radar de SLA, funil, OTD, ciclo e excecoes a partir das malas lidas. */
class CalculoPainelGestaoTest {

    private final Instant agora = Instant.parse("2026-11-26T18:00:00Z");
    private final Instant inicioHoje = Instant.parse("2026-11-26T03:00:00Z");

    private MalaAberta aberta(String numero, Duration paraOPrazo, Instant retirada, String status, Duration semAtualizar, long tentativas) {
        return new MalaAberta(UUID.randomUUID(), UUID.randomUUID(), numero, "Passageiro", "GYN", "Motorista", status,
                agora.plus(paraOPrazo), retirada, agora.minus(semAtualizar), tentativas, tentativas > 0 ? "Passageiro ausente" : null, false, agora.minus(Duration.ofHours(10)));
    }

    private MalaEntregue entregue(String numero, Instant entregueEm, Duration atrasoSobreOPrazo, Duration ciclo, boolean fotoPorta,
            boolean recebedor, boolean avariaEntrega) {
        return new MalaEntregue(UUID.randomUUID(), UUID.randomUUID(), numero, "Passageiro", "GYN", "Motorista", entregueEm,
                entregueEm.minus(atrasoSobreOPrazo), entregueEm.minus(ciclo), fotoPorta, false, recebedor, false, avariaEntrega, entregueEm.minus(Duration.ofHours(10)));
    }

    @Test
    void separaRadarFunilOtdCicloEExcecoes() {
        List<MalaAberta> abertas = List.of(
                aberta("NO-PRAZO", Duration.ofHours(5), null, "AGUARDANDO_ATRIBUICAO", Duration.ofMinutes(10), 0),
                aberta("ALERTA", Duration.ofMinutes(90), agora.minus(Duration.ofHours(1)), "EM_ANDAMENTO", Duration.ofMinutes(20), 0),
                aberta("ATRASADA", Duration.ofHours(-3), agora.minus(Duration.ofHours(8)), "REABERTA", Duration.ofHours(6), 1));
        List<MalaEntregue> entregues = List.of(
                entregue("HOJE-OK", agora.minus(Duration.ofHours(2)), Duration.ofHours(-1), Duration.ofMinutes(60), true, true, false),
                entregue("HOJE-ATRASO", agora.minus(Duration.ofHours(1)), Duration.ofMinutes(30), Duration.ofMinutes(120), false, true, true),
                entregue("ONTEM", inicioHoje.minus(Duration.ofHours(5)), Duration.ofHours(-2), Duration.ofMinutes(30), true, false, false));

        PainelGestaoResponse painel = CalculoPainelGestao.calcular(abertas, entregues, agora, inicioHoje, 24, 4);

        assertThat(painel.radar().noPrazo()).isEqualTo(1);
        assertThat(painel.radar().alerta()).extracting(ItemMala::numero).containsExactly("ALERTA");
        assertThat(painel.radar().atrasadas()).extracting(ItemMala::numero).containsExactly("ATRASADA");
        assertThat(painel.funil()).isEqualTo(new PainelGestaoResponse.Funil(1, 2, 2, 1));
        assertThat(painel.otd()).isEqualTo(new PainelGestaoResponse.Otd(50.0, 66.7, 2, 3));
        assertThat(painel.ciclo()).isEqualTo(new PainelGestaoResponse.Ciclo(90L, 70L));
        assertThat(painel.excecoes().tentativasFrustradas()).extracting(ItemMala::detalhe).containsExactly("Passageiro ausente");
        assertThat(painel.excecoes().paradas()).extracting(ItemMala::numero).containsExactly("ATRASADA");
        // Sem foto na porta (HOJE-ATRASO) e sem prova de quem recebeu (ONTEM: sem BDO assinado nem nome).
        assertThat(painel.excecoes().evidenciasPendentes()).extracting(ItemMala::numero, ItemMala::detalhe)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("HOJE-ATRASO", CalculoPainelGestao.SEM_FOTO_PORTA),
                        org.assertj.core.groups.Tuple.tuple("ONTEM", CalculoPainelGestao.SEM_ASSINATURA));
        assertThat(painel.excecoes().avarias().naEntrega()).isEqualTo(1);
        assertThat(painel.excecoes().avarias().entregues30Dias()).isEqualTo(3);
    }
}
