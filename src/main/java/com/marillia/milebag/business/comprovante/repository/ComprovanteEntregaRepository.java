package com.marillia.milebag.business.comprovante.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.core.base.BaseRepository;

public interface ComprovanteEntregaRepository extends BaseRepository<ComprovanteEntrega, UUID> {

    Optional<ComprovanteEntrega> findByEntregaId(UUID entregaId);
}
