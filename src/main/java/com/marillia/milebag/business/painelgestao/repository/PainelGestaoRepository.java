package com.marillia.milebag.business.painelgestao.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.entrega.model.Entrega;

/**
 * Leituras agregadas do painel de gestao. Sao nativas porque juntam varios dominios (BDO, entrega, retirada,
 * comprovante, fotos, ocorrencias) numa consulta so; a RLS de cada tabela filtra a empresa (o TenantAspect aplica o
 * tenant em toda chamada de Repository). So leitura.
 *
 * <p>Prazo da mala: a janela reagendada, se houver; senao o fim do dia da data prevista do BDO (horario de Brasilia);
 * senao o registro do BDO mais o prazo padrao da empresa.</p>
 */
public interface PainelGestaoRepository extends Repository<Entrega, UUID> {

    String PRAZO = """
            COALESCE(e.janela_reagendamento_fim,
                     ((b.data_entrega_prevista + 1)::timestamp AT TIME ZONE 'America/Sao_Paulo'),
                     b.criado_em + make_interval(hours => :prazoHoras))
            """;

    /** Colunas: bdo, entrega, numero, passageiro, base, motorista, status, prazo, retirada, ultima atualizacao, tentativas, ultimo motivo, avaria na retirada. */
    @Query(value = """
            SELECT b.id, e.id, b.numero, b.passageiro_nome, ba.codigo_iata, u.nome,
                   COALESCE(e.status, 'AGUARDANDO_ATRIBUICAO'),
                   """ + PRAZO + """
                   , r.data_hora,
                   GREATEST(b.atualizado_em, e.atualizado_em, r.data_hora, t.ultima_posicao),
                   COALESCE(oc.tentativas, 0),
                   oc.ultimo_motivo,
                   COALESCE(r.avaria, false)
            FROM bdo b
            LEFT JOIN entrega e ON e.bdo_id = b.id
            LEFT JOIN base_aeroportuaria ba ON ba.id = b.base_id
            LEFT JOIN usuario u ON u.id = e.motorista_responsavel
            LEFT JOIN LATERAL (
                SELECT max(r.data_hora) AS data_hora,
                       bool_or(btrim(COALESCE(r.observacao_avaria, '')) <> ''
                               OR EXISTS (SELECT 1 FROM foto_entrega f WHERE f.retirada_id = r.id AND f.tipo = 'AVARIA_RETIRADA'
                                          AND f.substituida_em IS NULL)) AS avaria
                FROM retirada_bagagem r WHERE r.entrega_id = e.id AND r.descartada_em IS NULL) r ON true
            LEFT JOIN LATERAL (
                SELECT max(t.posicao_registrada_em) AS ultima_posicao FROM trajeto t WHERE t.entrega_id = e.id) t ON true
            LEFT JOIN LATERAL (
                SELECT count(*) AS tentativas, (array_agg(m.descricao ORDER BY o.data_hora DESC))[1] AS ultimo_motivo
                FROM ocorrencia o LEFT JOIN motivo_ocorrencia m ON m.id = o.motivo_ocorrencia_id
                WHERE o.entrega_id = e.id) oc ON true
            WHERE b.status_conferencia = 'CONFIRMADO'
              AND NOT EXISTS (SELECT 1 FROM bagagem_carona bc WHERE bc.bdo_id = b.id)
              AND NOT EXISTS (SELECT 1 FROM cancelamento_bdo c WHERE c.bdo_id = b.id)
              AND (e.id IS NULL OR e.status IN ('AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'EM_ANDAMENTO', 'REABERTA'))
            """, nativeQuery = true)
    List<Object[]> malasAbertas(@Param("prazoHoras") int prazoHoras);

    /** Colunas: bdo, entrega, numero, passageiro, base, motorista, entregue em, prazo, retirada, foto porta, foto BDO assinado, recebedor, avaria retirada, avaria entrega. */
    @Query(value = """
            SELECT b.id, e.id, b.numero, b.passageiro_nome, ba.codigo_iata, u.nome, c.data_hora,
                   """ + PRAZO + """
                   , r.data_hora,
                   EXISTS (SELECT 1 FROM foto_entrega f WHERE f.entrega_id = e.id AND f.tipo = 'PORTA' AND f.substituida_em IS NULL),
                   EXISTS (SELECT 1 FROM foto_entrega f WHERE f.entrega_id = e.id AND f.tipo = 'BDO_ASSINADO' AND f.substituida_em IS NULL),
                   btrim(COALESCE(c.nome_recebedor, '')) <> '',
                   COALESCE(r.avaria, false),
                   btrim(COALESCE(c.observacao_avaria, '')) <> ''
                       OR EXISTS (SELECT 1 FROM foto_entrega f WHERE f.entrega_id = e.id AND f.tipo = 'AVARIA_ENTREGA' AND f.substituida_em IS NULL)
            FROM entrega e
            JOIN bdo b ON b.id = e.bdo_id
            JOIN LATERAL (
                SELECT c.data_hora, c.nome_recebedor, c.observacao_avaria FROM comprovante_entrega c
                WHERE c.entrega_id = e.id ORDER BY c.data_hora DESC LIMIT 1) c ON true
            LEFT JOIN base_aeroportuaria ba ON ba.id = b.base_id
            LEFT JOIN usuario u ON u.id = e.motorista_responsavel
            LEFT JOIN LATERAL (
                SELECT max(r.data_hora) AS data_hora,
                       bool_or(btrim(COALESCE(r.observacao_avaria, '')) <> ''
                               OR EXISTS (SELECT 1 FROM foto_entrega f WHERE f.retirada_id = r.id AND f.tipo = 'AVARIA_RETIRADA'
                                          AND f.substituida_em IS NULL)) AS avaria
                FROM retirada_bagagem r WHERE r.entrega_id = e.id AND r.descartada_em IS NULL) r ON true
            WHERE e.status = 'ENCERRADA_COM_SUCESSO' AND c.data_hora >= :desde
            """, nativeQuery = true)
    List<Object[]> malasEntregues(@Param("prazoHoras") int prazoHoras, @Param("desde") Instant desde);
}
