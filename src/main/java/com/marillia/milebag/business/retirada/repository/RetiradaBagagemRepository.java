package com.marillia.milebag.business.retirada.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.retirada.model.RetiradaBagagem;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de retirada_bagagem (V48) isola por tenant. */
public interface RetiradaBagagemRepository extends BaseRepository<RetiradaBagagem, UUID> {

    /** A retirada pendente: do motorista atual, ainda sem viagem e nao descartada. */
    Optional<RetiradaBagagem> findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(
            UUID entregaId, UUID motoristaId);

    List<RetiradaBagagem> findByEntregaIdAndTrajetoIdIsNullAndDescartadaEmIsNull(UUID entregaId);

    Optional<RetiradaBagagem> findByEntregaIdAndIdLocal(UUID entregaId, String idLocal);

    List<RetiradaBagagem> findByEntregaIdOrderByDataHoraAsc(UUID entregaId);
}
