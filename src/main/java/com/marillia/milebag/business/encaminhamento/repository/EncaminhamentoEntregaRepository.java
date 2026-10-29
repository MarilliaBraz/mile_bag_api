package com.marillia.milebag.business.encaminhamento.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;

/** Sem RLS (V45): toda consulta recebe a companhia do token. */
public interface EncaminhamentoEntregaRepository extends JpaRepository<EncaminhamentoEntrega, UUID> {

    Optional<EncaminhamentoEntrega> findByBagagemIdAndCompanhiaIdAndEtapaNot(UUID bagagemId, UUID companhiaId, EtapaEntrega etapa);

    List<EncaminhamentoEntrega> findByBagagemIdInAndCompanhiaIdAndEtapaNot(Collection<UUID> bagagemIds, UUID companhiaId, EtapaEntrega etapa);
}
