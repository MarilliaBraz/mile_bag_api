package com.marillia.milebag.business.configuracao.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.core.base.BaseRepository;

public interface BaseAeroportuariaRepository extends BaseRepository<BaseAeroportuaria, UUID> {

    Optional<BaseAeroportuaria> findByCodigoIata(String codigoIata);
}
