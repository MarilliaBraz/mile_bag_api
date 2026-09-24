package com.marillia.milebag.business.bdo.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.core.base.BaseRepository;

/**
 * Isolamento por tenant garantido pela politica de Row Level Security da
 * tabela {@code bdo} (ver migracao V1) - nenhuma query aqui precisa (nem
 * deve) filtrar por {@code tenantId} manualmente.
 */
public interface BdoRepository extends BaseRepository<Bdo, UUID> {

    /**
     * Verificacao de possivel duplicidade (Edge Case do spec.md): considera
     * BDOs de qualquer {@code origem}, inclusive recebidos automaticamente
     * por e-mail (RF21) - correspondencia pelo numero do documento
     * (campo-chave, ver spec.md "Assumptions").
     */
    List<Bdo> findByNumero(String numero);

    /**
     * Lista de BDOs disponiveis para autoatribuicao (HU19, FR-007): ja
     * confirmados, de qualquer {@code origem} (inclusive {@code
     * EMAIL_AUTOMATICO}, RF21), mas ainda sem uma {@code Entrega}
     * associada. Nativa (nao JPQL) para o {@code NOT EXISTS} contra a
     * tabela de outro dominio ficar explicito, mesmo padrao ja usado em
     * {@code EntregaRepository.autoatribuirSeDisponivel} - a policy de RLS
     * de {@code entrega} tambem se aplica dentro da subquery, entao so
     * enxerga entregas do mesmo tenant (irrelevante aqui: um bdo_id so
     * pertence a entregas do proprio tenant do BDO).
     */
    @Query(value = """
            SELECT b.* FROM bdo b
            WHERE b.status_conferencia = 'CONFIRMADO'
              AND NOT EXISTS (SELECT 1 FROM entrega e WHERE e.bdo_id = b.id)
              AND NOT EXISTS (SELECT 1 FROM bagagem_carona bc WHERE bc.bdo_id = b.id)
            ORDER BY b.data_captura ASC
            """, nativeQuery = true)
    List<Bdo> findConfirmadosSemEntrega();

    /** "Volume mensal de BDOs" do tenant (HU16, uso atual contra o limite do plano) - contagem por data de captura, mes corrente. */
    long countByDataCapturaBetween(Instant inicio, Instant fim);
}
