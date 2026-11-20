package com.marillia.milebag.business.cancelamento.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.cancelamento.model.CancelamentoBdo;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de cancelamento_bdo (V47) isola por tenant. */
public interface CancelamentoBdoRepository extends BaseRepository<CancelamentoBdo, UUID> {

    Optional<CancelamentoBdo> findByEntregaId(UUID entregaId);
}
