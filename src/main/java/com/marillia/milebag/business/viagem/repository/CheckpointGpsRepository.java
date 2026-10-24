package com.marillia.milebag.business.viagem.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.viagem.model.CheckpointGps;
import com.marillia.milebag.business.viagem.model.TipoCheckpoint;
import com.marillia.milebag.core.base.BaseRepository;

/** Sem metodo de update exposto (imutavel por registro): {@link CheckpointGps} nao tem setters. */
public interface CheckpointGpsRepository extends BaseRepository<CheckpointGps, UUID> {

    List<CheckpointGps> findByTrajetoIdOrderByDataHoraAsc(UUID trajetoId);

    /** HU20: o aviso ao passageiro sai so no primeiro registro de cada marco do trajeto. */
    boolean existsByTrajetoIdAndTipo(UUID trajetoId, TipoCheckpoint tipo);

    /** "Marco mais recente" do painel de entregas (HU20, FR-006) - data-model.md desta HU: derivado, nao persistido. */
    Optional<CheckpointGps> findFirstByTrajetoIdAndTipoInOrderByDataHoraDesc(UUID trajetoId, List<TipoCheckpoint> tipos);
}
