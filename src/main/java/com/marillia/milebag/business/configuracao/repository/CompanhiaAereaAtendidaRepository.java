package com.marillia.milebag.business.configuracao.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.configuracao.model.CompanhiaAereaAtendida;
import com.marillia.milebag.core.base.BaseRepository;

public interface CompanhiaAereaAtendidaRepository extends BaseRepository<CompanhiaAereaAtendida, UUID> {

    Optional<CompanhiaAereaAtendida> findByCodigoIata(String codigoIata);
}
