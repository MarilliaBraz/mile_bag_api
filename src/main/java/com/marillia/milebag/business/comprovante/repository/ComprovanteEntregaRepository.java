package com.marillia.milebag.business.comprovante.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.core.base.BaseRepository;

public interface ComprovanteEntregaRepository extends BaseRepository<ComprovanteEntrega, UUID> {

    Optional<ComprovanteEntrega> findByEntregaId(UUID entregaId);

    /** Usado por HU12 (fechamento) para achar as entregas concluidas dentro de um periodo (FR-004). */
    List<ComprovanteEntrega> findByDataHoraBetween(Instant inicio, Instant fim);
}
