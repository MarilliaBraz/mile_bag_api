package com.marillia.milebag.business.viagem.model;

import java.time.LocalDate;
import java.util.List;

import com.marillia.milebag.business.pagamento.model.SituacaoPagamentoMotorista;

/** Relatorio sob demanda (nao persistido) das entregas do motorista com trajeto consolidado no periodo (HU17, FR-001). */
public record RelatorioQuinzenalResponse(
        LocalDate periodoInicio,
        LocalDate periodoFim,
        List<ItemRelatorioMotoristaResponse> entregas,
        /** V56: a empresa ocultou a tarifa por viagem - o relatorio traz so km, cidade base e pedagio. */
        boolean tarifaOculta,
        /** V60: pagamento da quinzena (agendado para, pago em); nulo se a empresa ainda nao fechou exatamente este periodo. */
        SituacaoPagamentoMotorista pagamento) {
}
