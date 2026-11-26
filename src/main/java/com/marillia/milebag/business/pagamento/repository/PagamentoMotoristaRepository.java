package com.marillia.milebag.business.pagamento.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.pagamento.model.PagamentoMotorista;
import com.marillia.milebag.business.pagamento.model.SituacaoPagamento;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de pagamento_motorista (V60) isola por tenant. */
public interface PagamentoMotoristaRepository extends BaseRepository<PagamentoMotorista, UUID> {

    List<PagamentoMotorista> findByFechamentoId(UUID fechamentoId);

    Optional<PagamentoMotorista> findByFechamentoIdAndMotoristaId(UUID fechamentoId, UUID motoristaId);

    List<PagamentoMotorista> findBySituacao(SituacaoPagamento situacao);
}
