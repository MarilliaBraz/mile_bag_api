package com.marillia.milebag.business.painelgestao.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Painel de gestao, fase 1: pulso da operacao (radar de SLA, funil do dia, OTD e tempo de ciclo) e as excecoes
 * criticas. Contagens por BDO (a bagagem carona segue com a entrega principal).
 */
public record PainelGestaoResponse(
        Instant geradoEm,
        int prazoPadraoHoras,
        int horasSemAtualizacao,
        int metaOtd,
        Radar radar,
        Funil funil,
        Otd otd,
        Ciclo ciclo,
        Excecoes excecoes,
        List<DiaOperacao> serie) {

    /** Um dia dos ultimos 30 (horario de Brasilia): entregues, % no prazo e tempo medio base -> porta (nulos sem entrega). */
    public record DiaOperacao(LocalDate dia, long entregues, Double otd, Long cicloMinutos) {
    }

    /** Malas em aberto pelo prazo: no prazo, em alerta (menos de 2 h) e atrasadas. */
    public record Radar(long noPrazo, List<ItemMala> alerta, List<ItemMala> atrasadas) {
    }

    public record Funil(long aguardandoColeta, long emTransito, long entreguesHoje, long comOcorrencia) {
    }

    /** Percentual entregue dentro do prazo; nulo quando nada foi entregue no periodo. */
    public record Otd(Double hoje, Double trintaDias, long entreguesHoje, long entregues30Dias) {
    }

    /** Media, em minutos, da retirada no aeroporto ate o comprovante na porta; nula sem entregas no periodo. */
    public record Ciclo(Long minutosHoje, Long minutos30Dias) {
    }

    public record Excecoes(List<ItemMala> tentativasFrustradas, List<ItemMala> paradas, List<ItemMala> evidenciasPendentes,
            Avarias avarias) {
    }

    /** Ultimos 30 dias: malas com avaria registrada na retirada e na entrega, sobre o total entregue. */
    public record Avarias(long naRetirada, long naEntrega, long entregues30Dias, List<ItemMala> itens) {
    }

    /** Uma mala numa lista do painel; {@code referencia} e o horario que importa na lista e {@code detalhe} o motivo. */
    public record ItemMala(UUID bdoId, UUID entregaId, String numero, String passageiro, String base, String motorista,
            Instant prazo, Instant referencia, String detalhe) {
    }
}
