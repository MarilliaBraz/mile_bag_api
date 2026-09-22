package com.marillia.milebag.business.fechamento.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.core.base.BaseRepository;

public interface ItemFechamentoRepository extends BaseRepository<ItemFechamento, UUID> {

    List<ItemFechamento> findByFechamentoId(UUID fechamentoId);
}
