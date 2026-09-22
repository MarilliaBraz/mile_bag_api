package com.marillia.milebag.business.configuracao.model;

import java.time.LocalDate;

public record AgendarPeriodicidadeRequest(Periodicidade periodicidade, LocalDate vigenteDesde) {
}
