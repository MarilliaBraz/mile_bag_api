package com.marillia.milebag.business.encaminhamento.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.encaminhamento.model.CustoProcesso;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de custo_processo (V65) isola por companhia. */
public interface CustoProcessoRepository extends BaseRepository<CustoProcesso, UUID> {

    List<CustoProcesso> findByBagagemIdOrderByDataAscCriadoEmAsc(UUID bagagemId);

    Optional<CustoProcesso> findByIdAndBagagemId(UUID id, UUID bagagemId);
}
