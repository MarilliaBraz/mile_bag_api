package com.marillia.milebag.business.rastreabilidade.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item 9: auditoria do km de uma viagem encerrada da entrega - o calculado (ida e volta), o percorrido na ida (GPS; nulo
 * sem pontos) e o que entrou no pagamento por km. {@code valorFixo}: entrega na cidade da base, paga o valor fixo e o km
 * fica so como registro. {@code kmIdaPago}/{@code kmVoltaPago} nulos quando ainda nao ha valor calculavel.
 */
public record KmViagemResponse(
        Instant dataInicio,
        String motoristaNome,
        BigDecimal kmIdaCalculado,
        BigDecimal kmVoltaCalculado,
        BigDecimal kmPercorrido,
        BigDecimal kmIdaPago,
        BigDecimal kmVoltaPago,
        boolean usouPercorrido,
        boolean valorFixo) {
}
