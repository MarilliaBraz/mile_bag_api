package com.marillia.milebag.business.painelgestao.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.painelgestao.model.PainelGestaoConfig;
import com.marillia.milebag.core.base.BaseRepository;

/** Uma linha por empresa (RLS de painel_gestao_config, V61). */
public interface PainelGestaoConfigRepository extends BaseRepository<PainelGestaoConfig, UUID> {

    Optional<PainelGestaoConfig> findFirstByOrderByCriadoEmAsc();
}
