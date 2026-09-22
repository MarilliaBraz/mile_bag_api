package com.marillia.milebag.business.configuracao.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.configuracao.model.PeriodicidadeFechamentoConfig;
import com.marillia.milebag.core.base.BaseRepository;

public interface PeriodicidadeFechamentoConfigRepository extends BaseRepository<PeriodicidadeFechamentoConfig, UUID> {

    Optional<PeriodicidadeFechamentoConfig> findFirstByOrderByVigenteDesdeDesc();

    Optional<PeriodicidadeFechamentoConfig> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate data);

    Optional<PeriodicidadeFechamentoConfig> findFirstByVigenteDesdeGreaterThanOrderByVigenteDesdeAsc(LocalDate data);
}
