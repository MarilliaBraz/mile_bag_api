package com.marillia.milebag.business.pedagio.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.pedagio.model.PracaPedagioValor;
import com.marillia.milebag.core.base.BaseRepository;

public interface PracaPedagioValorRepository extends BaseRepository<PracaPedagioValor, UUID> {

    List<PracaPedagioValor> findByPracaPedagioIdOrderByVigenteDesdeDesc(UUID pracaPedagioId);

    Optional<PracaPedagioValor> findFirstByPracaPedagioIdOrderByVigenteDesdeDesc(UUID pracaPedagioId);

    /** O valor de maior vigenteDesde que seja <= a data informada (research.md desta HU). */
    Optional<PracaPedagioValor> findFirstByPracaPedagioIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            UUID pracaPedagioId, LocalDate data);
}
