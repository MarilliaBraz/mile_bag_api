package com.marillia.milebag.business.fotoentrega.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.fotoentrega.model.FotoEntrega;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de foto_entrega (V48) isola por tenant. */
public interface FotoEntregaRepository extends BaseRepository<FotoEntrega, UUID> {

    List<FotoEntrega> findByEntregaIdOrderByDataHoraAsc(UUID entregaId);
}
