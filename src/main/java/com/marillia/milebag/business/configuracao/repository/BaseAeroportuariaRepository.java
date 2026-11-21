package com.marillia.milebag.business.configuracao.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.core.base.BaseRepository;

public interface BaseAeroportuariaRepository extends BaseRepository<BaseAeroportuaria, UUID> {

    Optional<BaseAeroportuaria> findByCodigoIata(String codigoIata);

    /** V52: a principal primeiro, depois as demais na ordem em que foram cadastradas. */
    List<BaseAeroportuaria> findAllByOrderByPrincipalDescCriadoEmAsc();
}
