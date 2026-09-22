package com.marillia.milebag.business.configuracao.model;

import java.time.LocalDate;

/** Ver contracts/configuracoes-tenant-api.md - "a periodicidade vigente e a proxima, se ja houver uma agendada." */
public record PeriodicidadeAtualResponse(Periodicidade vigente, Periodicidade proxima, LocalDate proximaVigenteDesde) {
}
