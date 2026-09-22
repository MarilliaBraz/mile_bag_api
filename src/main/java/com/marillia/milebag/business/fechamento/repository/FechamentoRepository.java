package com.marillia.milebag.business.fechamento.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.fechamento.model.Fechamento;
import com.marillia.milebag.core.base.BaseRepository;

public interface FechamentoRepository extends BaseRepository<Fechamento, UUID> {

    Optional<Fechamento> findByPeriodoInicioAndPeriodoFim(LocalDate periodoInicio, LocalDate periodoFim);
}
