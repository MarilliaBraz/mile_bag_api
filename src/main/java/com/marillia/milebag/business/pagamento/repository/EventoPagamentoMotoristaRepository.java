package com.marillia.milebag.business.pagamento.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.pagamento.model.EventoPagamentoMotorista;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de evento_pagamento_motorista (V60) isola por tenant. */
public interface EventoPagamentoMotoristaRepository extends BaseRepository<EventoPagamentoMotorista, UUID> {

    List<EventoPagamentoMotorista> findByPagamentoMotoristaIdInOrderByDataHoraAsc(Collection<UUID> pagamentoMotoristaIds);
}
