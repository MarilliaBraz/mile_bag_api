package com.marillia.milebag.business.configuracao.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.configuracao.model.CampoBaixa;
import com.marillia.milebag.business.configuracao.model.CampoBaixaConfig;
import com.marillia.milebag.core.base.BaseRepository;

public interface CampoBaixaConfigRepository extends BaseRepository<CampoBaixaConfig, UUID> {

    Optional<CampoBaixaConfig> findByCampo(CampoBaixa campo);
}
