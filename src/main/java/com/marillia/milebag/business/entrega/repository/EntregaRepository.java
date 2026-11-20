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
     * Dispensa (V46): devolve a entrega aos motoristas so se ela ainda for do motorista e estiver em aberto, numa
     * unica instrucao - mesmo padrao de {@link #autoatribuirSeDisponivel}. Uma conclusao (comprovante) ou outra
     * dispensa simultanea faz esta devolver 0, e nada e alterado.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE entrega
               SET status = :novoStatus, motorista_responsavel = NULL, atualizado_em = now()
             WHERE id = :id
               AND motorista_responsavel = :motoristaId
               AND status IN ('ATRIBUIDA', 'EM_ANDAMENTO')
            """, nativeQuery = true)
    int devolverSeAindaDoMotorista(
            @Param("id") UUID id, @Param("motoristaId") UUID motoristaId, @Param("novoStatus") String novoStatus);

    /** Tela "Entregas" da empresa (V46) - limitada para nao crescer sem fim. */
    List<Entrega> findTop200ByOrderByAtualizadoEmDesc();

    List<Entrega> findTop200ByStatusOrderByAtualizadoEmDesc(EntregaStatus status);

    /**
     * Painel de entregas pendentes do motorista (HU19, FR-001) - os tres
     * status "em aberto" do ciclo de vida (RD05), nunca {@code
     * AGUARDANDO_ATRIBUICAO} (sem motorista ainda) nem {@code
     * ENCERRADA_COM_SUCESSO} (concluida). Ordenado por {@code atualizadoEm}
     * (nao {@code criadoEm}): para uma entrega reaberta (HU07) e
     * reatribuida, {@code autoatribuirSeDisponivel} atualiza a MESMA linha
     * (UPSERT por {@code bdo_id}) - {@code atualizadoEm} reflete a
     * atribuicao atual, {@code criadoEm} ficaria preso na primeira
     * atribuicao (data-model.md desta HU, campo {@code dataAutoatribuicao}).
     */
    List<Entrega> findByMotoristaResponsavelAndStatusInOrderByAtualizadoEmAsc(
            UUID motoristaResponsavel, List<EntregaStatus> status);

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
