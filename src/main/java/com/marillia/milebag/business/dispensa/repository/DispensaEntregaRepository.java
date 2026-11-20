package com.marillia.milebag.business.dispensa.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.dispensa.model.DispensaEntrega;
import com.marillia.milebag.core.base.BaseRepository;

public interface DispensaEntregaRepository extends BaseRepository<DispensaEntrega, UUID> {

    List<DispensaEntrega> findByEntregaIdOrderByDataHoraAsc(UUID entregaId);
}
