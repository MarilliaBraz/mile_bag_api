package com.marillia.milebag.business.basebdo.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.basebdo.model.TrocaBaseBdo;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de troca_base_bdo (V52) isola por tenant. */
public interface TrocaBaseBdoRepository extends BaseRepository<TrocaBaseBdo, UUID> {

    List<TrocaBaseBdo> findByBdoIdOrderByDataHoraAsc(UUID bdoId);
}
