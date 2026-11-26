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

    /** Colunas: bdo, entrega, numero, passageiro, base, motorista, status, prazo, retirada, ultima atualizacao, tentativas, ultimo motivo, avaria na retirada, registro do BDO. */
    @Query(value = """
            SELECT b.id, e.id, b.numero, b.passageiro_nome, ba.codigo_iata, u.nome,
                   COALESCE(e.status, 'AGUARDANDO_ATRIBUICAO'),
                   """ + PRAZO + """
                   , r.data_hora,
                   GREATEST(b.atualizado_em, e.atualizado_em, r.data_hora, t.ultima_posicao),
                   COALESCE(oc.tentativas, 0),
                   oc.ultimo_motivo,
                   COALESCE(r.avaria, false),
                   b.criado_em
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

    /** Colunas: bdo, entrega, numero, passageiro, base, motorista, entregue em, prazo, retirada, foto porta, foto BDO assinado, recebedor, avaria retirada, avaria entrega, registro do BDO. */
    @Query(value = """
            SELECT b.id, e.id, b.numero, b.passageiro_nome, ba.codigo_iata, u.nome, c.data_hora,
                   """ + PRAZO + """
                   , r.data_hora,
                   EXISTS (SELECT 1 FROM foto_entrega f WHERE f.entrega_id = e.id AND f.tipo = 'PORTA' AND f.substituida_em IS NULL),
                   EXISTS (SELECT 1 FROM foto_entrega f WHERE f.entrega_id = e.id AND f.tipo = 'BDO_ASSINADO' AND f.substituida_em IS NULL),
                   btrim(COALESCE(c.nome_recebedor, '')) <> '',
                   COALESCE(r.avaria, false),
                   btrim(COALESCE(c.observacao_avaria, '')) <> ''
                       OR EXISTS (SELECT 1 FROM foto_entrega f WHERE f.entrega_id = e.id AND f.tipo = 'AVARIA_ENTREGA' AND f.substituida_em IS NULL),
                   b.criado_em
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

    /** Fase 2 - bases da empresa com a posicao do aeroporto. Colunas: codigo, nome, cidade, latitude, longitude. */
    @Query(value = """
            SELECT ba.codigo_iata, ba.nome, a.cidade, a.latitude, a.longitude
            FROM base_aeroportuaria ba
            LEFT JOIN aeroporto a ON a.codigo = ba.codigo_iata
            ORDER BY ba.principal DESC, ba.codigo_iata
            """, nativeQuery = true)
    List<Object[]> bases();

    /**
     * Fase 2 - viagens em execucao (iniciadas ha ate 48 h), com a rota escolhida e o caminho do GPS. Colunas: trajeto,
     * entrega, numero, motorista, base, cidade, uf, inicio, metros previstos (ida), tem pedagio, pracas, entregue,
     * metros percorridos, ultima posicao em, latitude, longitude, posicoes nos ultimos minutos, deslocamento nesses
     * minutos (metros, pela diagonal da area percorrida).
     */
    @Query(value = """
            WITH ativos AS (
                SELECT t.id, t.entrega_id, t.motorista_id, t.data_inicio
                FROM trajeto t
                WHERE t.status = 'EM_EXECUCAO' AND t.data_inicio >= now() - interval '48 hours'),
            pos AS (
                SELECT p.trajeto_id, p.data_hora, p.latitude, p.longitude,
                       lag(p.latitude) OVER w AS lat0, lag(p.longitude) OVER w AS lng0
                FROM posicao_viagem p JOIN ativos a ON a.id = p.trajeto_id
                WHERE p.precisao_metros IS NULL OR p.precisao_metros <= 100
                WINDOW w AS (PARTITION BY p.trajeto_id ORDER BY p.data_hora)),
            caminho AS (
                SELECT trajeto_id,
                       sum(CASE WHEN lat0 IS NULL THEN 0 ELSE 2 * 6371000 * asin(sqrt(
                           power(sin(radians(latitude - lat0) / 2), 2)
                           + cos(radians(lat0)) * cos(radians(latitude)) * power(sin(radians(longitude - lng0) / 2), 2))) END) AS metros,
                       max(data_hora) AS ultima,
                       (array_agg(latitude ORDER BY data_hora DESC))[1] AS lat,
                       (array_agg(longitude ORDER BY data_hora DESC))[1] AS lng,
                       count(*) FILTER (WHERE data_hora >= now() - make_interval(mins => :minutosParado)) AS recentes,
                       sqrt(power((max(latitude) FILTER (WHERE data_hora >= now() - make_interval(mins => :minutosParado))
                                   - min(latitude) FILTER (WHERE data_hora >= now() - make_interval(mins => :minutosParado))) * 111320, 2)
                            + power((max(longitude) FILTER (WHERE data_hora >= now() - make_interval(mins => :minutosParado))
                                   - min(longitude) FILTER (WHERE data_hora >= now() - make_interval(mins => :minutosParado)))
                                   * 111320 * cos(radians(max(latitude))), 2)) AS deslocamento
                FROM pos GROUP BY trajeto_id)
            SELECT a.id, a.entrega_id, b.numero, u.nome, ba.codigo_iata, b.cidade, b.uf, a.data_inicio,
                   (pr.rota_escolhida ->> 'distanciaMetros')::double precision,
                   COALESCE((pr.rota_escolhida ->> 'possuiPedagio')::boolean, false),
                   COALESCE(jsonb_array_length(pr.rota_escolhida -> 'pracasPedagio'), 0),
                   EXISTS (SELECT 1 FROM comprovante_entrega c WHERE c.entrega_id = a.entrega_id),
                   COALESCE(c.metros, 0), c.ultima, c.lat, c.lng, COALESCE(c.recentes, 0), c.deslocamento
            FROM ativos a
            JOIN entrega e ON e.id = a.entrega_id
            JOIN bdo b ON b.id = e.bdo_id
            LEFT JOIN base_aeroportuaria ba ON ba.id = b.base_id
            LEFT JOIN usuario u ON u.id = a.motorista_id
            LEFT JOIN proposta_rota pr ON pr.entrega_id = a.entrega_id
            LEFT JOIN caminho c ON c.trajeto_id = a.id
            ORDER BY a.data_inicio
            """, nativeQuery = true)
    List<Object[]> rotasAtivas(@Param("minutosParado") int minutosParado);
}
