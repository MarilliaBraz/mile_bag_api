package com.marillia.milebag.business.tarifa.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.tarifa.model.EscopoTarifa;
import com.marillia.milebag.business.tarifa.model.TabelaTarifa;
import com.marillia.milebag.core.base.BaseRepository;

/** Isolamento por tenant garantido por Row Level Security (V15), como em todo repositorio deste projeto. */
public interface TabelaTarifaRepository extends BaseRepository<TabelaTarifa, UUID> {

    Optional<TabelaTarifa> findByEscopo(EscopoTarifa escopo);

    Optional<TabelaTarifa> findByEscopoAndMotoristaId(EscopoTarifa escopo, UUID motoristaId);

    Optional<TabelaTarifa> findByEscopoAndRegiao(EscopoTarifa escopo, String regiao);
}
