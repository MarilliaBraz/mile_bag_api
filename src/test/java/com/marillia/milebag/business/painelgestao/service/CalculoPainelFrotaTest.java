package com.marillia.milebag.business.painelgestao.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.painelgestao.model.MotoristaPeriodo;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.MotoristaStatus;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.Praca;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.SituacaoMotorista;
import com.marillia.milebag.business.painelgestao.model.ViagemPeriodo;

/** Fase 3: custo por mala (com a tentativa sem sucesso no custo), malas por saida e situacao dos motoristas. */
class CalculoPainelFrotaTest {

    private final Instant agora = Instant.parse("2026-11-26T18:00:00Z");
    private final Instant inicioHoje = Instant.parse("2026-11-26T03:00:00Z");
    private final Instant inicio30Dias = inicioHoje.minus(Duration.ofDays(30));
    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    private ViagemPeriodo viagem(UUID motorista, Instant encerrada, long malas, boolean entregue, String pagamento, String pedagio) {
        return new ViagemPeriodo(UUID.randomUUID(), motorista, encerrada.minus(Duration.ofHours(1)), encerrada, 40, malas, entregue,
                pagamento == null ? null : new BigDecimal(pagamento), pedagio == null ? null : new BigDecimal(pedagio));
    }

    @Test
    void calculaCustoOcupacaoEMotoristas() {
        List<ViagemPeriodo> viagens = List.of(
                viagem(ana, agora.minus(Duration.ofHours(2)), 3, true, "90.00", "10.00"),
                viagem(ana, agora.minus(Duration.ofHours(1)), 1, false, "30.00", "0.00"),
                viagem(bruno, inicioHoje.minus(Duration.ofDays(2)), 1, true, null, "0.00"));
        List<MotoristaPeriodo> motoristas = List.of(
                new MotoristaPeriodo(ana, "Ana", "GYN", agora.minus(Duration.ofMinutes(5)), false, 10, 9, 8),
                new MotoristaPeriodo(bruno, "Bruno", "GYN", agora.minus(Duration.ofHours(3)), false, 4, 2, 4));

        PainelFrotaResponse painel = CalculoPainelFrota.calcular(viagens, motoristas, agora, inicioHoje, inicio30Dias);

        // Hoje: 130,00 (inclusive a tentativa sem sucesso) por 3 malas entregues.
        assertThat(painel.custoHoje().total()).isEqualByComparingTo("130.00");
        assertThat(painel.custoHoje().porMala()).isEqualByComparingTo("43.33");
        assertThat(painel.custo30Dias().viagensSemValor()).isEqualTo(1);
        assertThat(painel.custo30Dias().malasEntregues()).isEqualTo(4);
        assertThat(painel.ocupacao30Dias().saidas()).isEqualTo(3);
        assertThat(painel.ocupacao30Dias().malasPorSaida()).isEqualTo(1.7);
        assertThat(painel.ocupacao30Dias().saidasComUmaMala()).isEqualTo(2);
        assertThat(painel.motoristas()).extracting(MotoristaStatus::nome, MotoristaStatus::situacao, MotoristaStatus::pontualidade)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ana", SituacaoMotorista.DISPONIVEL, 90.0),
                        org.assertj.core.groups.Tuple.tuple("Bruno", SituacaoMotorista.OFFLINE, 50.0));
        assertThat(painel.pracas()).containsExactly(new Praca("GYN", 0, 1, 1));
    }
}
