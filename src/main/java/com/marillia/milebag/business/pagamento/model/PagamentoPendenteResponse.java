package com.marillia.milebag.business.pagamento.model;

import java.time.LocalDate;
import java.util.UUID;

/** Painel de atividades: pagamento de quinzena ainda nao feito (a agendar ou agendado), com o periodo do fechamento. */
public record PagamentoPendenteResponse(
        UUID fechamentoId,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        UUID motoristaId,
        SituacaoPagamento situacao,
        LocalDate dataAgendada) {
}
