package com.marillia.milebag.business.ocorrencia.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.ocorrencia.model.Ocorrencia;
import com.marillia.milebag.core.base.BaseRepository;

public interface OcorrenciaRepository extends BaseRepository<Ocorrencia, UUID> {

    List<Ocorrencia> findByEntregaIdOrderByDataHoraAsc(UUID entregaId);

    Optional<Ocorrencia> findByEntregaIdAndIdLocal(UUID entregaId, String idLocal);
}
