package com.marillia.milebag.business.encaminhamento.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;

/** Sem RLS (V45): toda consulta recebe a companhia do token. */
public interface EncaminhamentoEntregaRepository extends JpaRepository<EncaminhamentoEntrega, UUID> {

    Optional<EncaminhamentoEntrega> findByBagagemIdAndCompanhiaIdAndEtapaNot(UUID bagagemId, UUID companhiaId, EtapaEntrega etapa);

    long countByCompanhiaIdAndEtapaIn(UUID companhiaId, Collection<EtapaEntrega> etapas);

    /**
     * Painel de atividades: bagagens registradas da companhia (a RLS de bagagem_encontrada ja filtra) sem encaminhamento
     * ativo - as que ainda esperam ser mandadas para uma empresa de entrega.
     */
    @Query(value = """
            SELECT count(*) FROM bagagem_encontrada b
            WHERE b.situacao = 'REGISTRADA'
              AND NOT EXISTS (SELECT 1 FROM encaminhamento_entrega e
                              WHERE e.bagagem_id = b.id AND e.companhia_id = :companhiaId AND e.etapa <> 'CANCELADO')
            """, nativeQuery = true)
    long contarRegistradasSemEncaminhamento(@Param("companhiaId") UUID companhiaId);

    List<EncaminhamentoEntrega> findByBagagemIdInAndCompanhiaIdAndEtapaNot(Collection<UUID> bagagemIds, UUID companhiaId, EtapaEntrega etapa);
}
