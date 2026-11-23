package com.marillia.milebag.business.dadosbdo.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.dadosbdo.model.AlteracaoDadosBdo;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de alteracao_dados_bdo (V59) isola por tenant. */
public interface AlteracaoDadosBdoRepository extends BaseRepository<AlteracaoDadosBdo, UUID> {

    List<AlteracaoDadosBdo> findByBdoIdOrderByDataHoraAsc(UUID bdoId);
}
