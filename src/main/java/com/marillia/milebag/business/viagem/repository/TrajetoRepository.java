package com.marillia.milebag.business.viagem.repository;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.core.base.BaseRepository;

/**
 * Sem metodo de atualizacao alem de {@code save()} sobre uma entidade ja
 * carregada - a imutabilidade por campo e garantida pela ausencia de
 * setters em {@link Trajeto} (so {@link Trajeto#consolidar} muda estado).
 */
public interface TrajetoRepository extends BaseRepository<Trajeto, UUID> {

    Optional<Trajeto> findByEntregaId(UUID entregaId);
}
