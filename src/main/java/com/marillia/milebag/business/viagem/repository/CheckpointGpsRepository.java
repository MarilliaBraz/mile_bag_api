package com.marillia.milebag.business.viagem.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.viagem.model.CheckpointGps;
import com.marillia.milebag.core.base.BaseRepository;

/** Sem metodo de update exposto (imutavel por registro): {@link CheckpointGps} nao tem setters. */
public interface CheckpointGpsRepository extends BaseRepository<CheckpointGps, UUID> {

    List<CheckpointGps> findByTrajetoIdOrderByDataHoraAsc(UUID trajetoId);
}
