package com.marillia.milebag.business.encaminhamento.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;

/**
 * Leituras da entrega do parceiro para a companhia (fase 2). Rodam com o tenant da EMPRESA de entrega (a RLS das
 * tabelas dela); quem chama garante que o BDO e do encaminhamento ativo da companhia do token. Nada do motorista.
 */
public interface EntregaParceiroRepository extends Repository<EncaminhamentoEntrega, UUID> {

    /** A entrega do BDO; para uma bagagem carona, a entrega principal com que ela viaja. */
    @Query(value = """
            SELECT id FROM (
                SELECT e.id, 0 AS ordem FROM entrega e WHERE e.bdo_id = :bdoId
                UNION ALL
                SELECT bc.entrega_principal_id, 1 FROM bagagem_carona bc WHERE bc.bdo_id = :bdoId) x
            ORDER BY ordem LIMIT 1
            """, nativeQuery = true)
    List<UUID> entregaDoBdo(@Param("bdoId") UUID bdoId);

    /** Colunas: data e hora, nome de quem recebeu, tipo de recebedor, latitude, longitude. */
    @Query(value = """
            SELECT c.data_hora, c.nome_recebedor, m.descricao, c.latitude, c.longitude
            FROM comprovante_entrega c LEFT JOIN motivo_ocorrencia m ON m.id = c.tipo_recebedor_id
            WHERE c.entrega_id = :entregaId ORDER BY c.data_hora DESC LIMIT 1
            """, nativeQuery = true)
    List<Object[]> comprovante(@Param("entregaId") UUID entregaId);

    /** A ultima tentativa sem sucesso. Colunas: data e hora, motivo, observacao. */
    @Query(value = """
            SELECT o.data_hora, m.descricao, o.descricao_adicional
            FROM ocorrencia o LEFT JOIN motivo_ocorrencia m ON m.id = o.motivo_ocorrencia_id
            WHERE o.entrega_id = :entregaId ORDER BY o.data_hora DESC LIMIT 1
            """, nativeQuery = true)
    List<Object[]> ultimaOcorrencia(@Param("entregaId") UUID entregaId);

    /** Contato do passageiro, que so fica no BDO (V45). Colunas: telefone, e-mail. */
    @Query(value = "SELECT telefone_passageiro, email_passageiro FROM bdo WHERE id = :bdoId", nativeQuery = true)
    List<Object[]> contato(@Param("bdoId") UUID bdoId);

    /** O link publico mais recente de "Acompanhe sua bagagem" (sem RLS: o codigo e o que identifica). */
    @Query(value = "SELECT codigo FROM link_acompanhamento WHERE entrega_id = :entregaId ORDER BY criado_em DESC LIMIT 1", nativeQuery = true)
    List<UUID> linkAcompanhamento(@Param("entregaId") UUID entregaId);
}
