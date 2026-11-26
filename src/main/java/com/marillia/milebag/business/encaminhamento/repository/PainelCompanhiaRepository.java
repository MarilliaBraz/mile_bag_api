package com.marillia.milebag.business.encaminhamento.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;

/**
 * Leituras do painel de gestao da companhia aerea. encaminhamento_entrega nao tem RLS: toda consulta recebe a
 * companhia do token; bagagem_encontrada tem (o TenantAspect aplica a companhia em toda chamada de Repository).
 */
public interface PainelCompanhiaRepository extends Repository<EncaminhamentoEntrega, UUID> {

    /**
     * Encaminhamentos em andamento e os entregues desde a data. Colunas: bagagem, processo, passageiro, etapa, tipo de
     * voo, processo aberto em, encaminhado em, coletado em, entregue em, nao entregue em, aeroporto, registrada em,
     * empresa de entrega.
     */
    @Query(value = """
            SELECT e.bagagem_id, e.numero_processo, e.passageiro_nome, e.etapa, e.tipo_voo, e.processo_aberto_em, e.encaminhado_em,
                   e.coletado_em, e.entregue_em, e.nao_entregue_em, b.aeroporto_codigo, b.registrada_em, t.nome
            FROM encaminhamento_entrega e
            JOIN bagagem_encontrada b ON b.id = e.bagagem_id
            LEFT JOIN tenant t ON t.id = e.empresa_id
            WHERE e.companhia_id = :companhiaId AND e.etapa <> 'CANCELADO'
              AND (e.etapa <> 'ENTREGUE' OR e.entregue_em >= :desde)
            """, nativeQuery = true)
    List<Object[]> encaminhamentos(@Param("companhiaId") UUID companhiaId, @Param("desde") Instant desde);

    /** Bagagens registradas ainda na sala de bagagens (sem encaminhamento ativo). Colunas: id, aeroporto, registrada em, etiqueta ou SID, passageiro. */
    @Query(value = """
            SELECT b.id, b.aeroporto_codigo, b.registrada_em, COALESCE(b.numero_etiqueta, b.codigo_sid), b.nome_passageiro
            FROM bagagem_encontrada b
            WHERE b.situacao = 'REGISTRADA'
              AND NOT EXISTS (SELECT 1 FROM encaminhamento_entrega e
                              WHERE e.bagagem_id = b.id AND e.companhia_id = :companhiaId AND e.etapa <> 'CANCELADO')
            """, nativeQuery = true)
    List<Object[]> naSala(@Param("companhiaId") UUID companhiaId);
}
