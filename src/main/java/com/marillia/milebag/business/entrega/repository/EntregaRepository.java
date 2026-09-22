package com.marillia.milebag.business.entrega.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.core.base.BaseRepository;

public interface EntregaRepository extends BaseRepository<Entrega, UUID> {

    Optional<Entrega> findByBdoId(UUID bdoId);

    List<Entrega> findByMotoristaResponsavelAndStatus(UUID motoristaResponsavel, EntregaStatus status);

    /**
     * Cria a Entrega (se ainda nao existir para este BDO) ou atribui o
     * motorista a uma ja existente, em uma unica instrucao atomica -
     * garante a unicidade do motorista responsavel por constraint/instrucao
     * de banco, nao apenas checagem em memoria (research.md desta HU,
     * "a unicidade ... deve ser garantida por uma constraint no banco de
     * dados"), evitando a condicao de corrida de dois motoristas
     * autoatribuindo a mesma entrega quase simultaneamente (Edge Case do
     * spec.md).
     *
     * <p>Retorna 1 quando a atribuicao (criacao ou atualizacao) foi bem
     * sucedida; 0 quando ja havia um motorista responsavel ou o status nao
     * era elegivel - nesses casos nada e alterado (FR-004).</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO entrega (id, tenant_id, bdo_id, motorista_responsavel, status, criado_em, atualizado_em)
            VALUES (:id, :tenantId, :bdoId, :motoristaId, 'ATRIBUIDA', now(), now())
            ON CONFLICT (bdo_id) DO UPDATE
                SET motorista_responsavel = EXCLUDED.motorista_responsavel,
                    status = 'ATRIBUIDA',
                    atualizado_em = now()
                WHERE entrega.motorista_responsavel IS NULL
                  AND entrega.status IN ('AGUARDANDO_ATRIBUICAO', 'REABERTA')
            """, nativeQuery = true)
    int autoatribuirSeDisponivel(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("bdoId") UUID bdoId,
            @Param("motoristaId") UUID motoristaId);
}
