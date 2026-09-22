package com.marillia.milebag.business.viagem.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.viagem.model.CalculoRota;
import com.marillia.milebag.core.base.BaseRepository;

/** Sem metodo de update exposto (imutavel por registro - FR-004): {@link CalculoRota} nao tem setters. */
public interface CalculoRotaRepository extends BaseRepository<CalculoRota, UUID> {

    List<CalculoRota> findByTrajetoIdOrderByDataHoraAsc(UUID trajetoId);
}
