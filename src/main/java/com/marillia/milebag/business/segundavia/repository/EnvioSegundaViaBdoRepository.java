package com.marillia.milebag.business.segundavia.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.segundavia.model.EnvioSegundaViaBdo;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de envio_segunda_via_bdo (V50) isola por tenant. */
public interface EnvioSegundaViaBdoRepository extends BaseRepository<EnvioSegundaViaBdo, UUID> {

    List<EnvioSegundaViaBdo> findByBdoIdOrderByDataHoraAsc(UUID bdoId);
}
