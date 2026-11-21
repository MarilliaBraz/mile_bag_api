package com.marillia.milebag.business.bdo.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * EMAIL_AUTOMATICO}, RF21), sem motorista responsavel - ou seja, sem
     * {@code Entrega} ainda, ou com uma entrega reaberta por ocorrencia
     * (RN05: {@code REABERTA} e sem motorista) ou devolvida por uma dispensa
     * antes de qualquer tentativa (V46: {@code AGUARDANDO_ATRIBUICAO} e sem
     * motorista). Antes de HU29 a entrega
     * reaberta ficava fora desta lista e tambem fora do painel (que exige
     * motorista), invisivel para todos. BDO vinculado como bagagem carona
     * viaja com outra entrega e nunca aparece.
     *
     * <p>Nativa (nao JPQL) para o {@code NOT EXISTS} contra a tabela de outro
     * dominio ficar explicito, mesmo padrao de {@code
     * EntregaRepository.autoatribuirSeDisponivel} - a policy de RLS de
     * {@code entrega} tambem se aplica dentro da subquery.</p>
     */
    @Query(value = CONSULTA_DISPONIVEIS_PARA_AUTOATRIBUICAO, nativeQuery = true)
    List<Bdo> findDisponiveisParaAutoatribuicao();

    /** Constante para o teste contra PostgreSQL real exercitar exatamente este SQL (BdosDisponiveisConsultaTest). */
    String CONSULTA_DISPONIVEIS_PARA_AUTOATRIBUICAO = """
            SELECT b.* FROM bdo b
            WHERE b.status_conferencia = 'CONFIRMADO'
              AND NOT EXISTS (
                  SELECT 1 FROM entrega e
                  WHERE e.bdo_id = b.id
                    AND NOT (e.status IN ('REABERTA', 'AGUARDANDO_ATRIBUICAO') AND e.motorista_responsavel IS NULL))
              AND NOT EXISTS (SELECT 1 FROM bagagem_carona bc WHERE bc.bdo_id = b.id)
            ORDER BY b.data_captura ASC
            """;

    /**
     * "Volume mensal de BDOs" do tenant (HU16, uso atual contra o limite do plano) - contagem por data de captura, mes corrente.
     * So conta BDO confirmado: uma foto descartada ou abandonada na conferencia nao e um BDO de verdade. Nem o BDO
     * cancelado pela empresa (V47): enviado por engano ou duplicado, nao foi um servico.
     */
    @Query(value = CONSULTA_VOLUME_DO_PLANO, nativeQuery = true)
    long contarConfirmadosNaoCanceladosEntre(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /** Constante para o teste contra PostgreSQL real exercitar exatamente este SQL (CancelamentoBdoBancoTest). */
    String CONSULTA_VOLUME_DO_PLANO = """
            SELECT count(*) FROM bdo b
            WHERE b.status_conferencia = 'CONFIRMADO'
              AND b.data_captura BETWEEN :inicio AND :fim
              AND NOT EXISTS (SELECT 1 FROM cancelamento_bdo c WHERE c.bdo_id = b.id)
            """;

    /**
     * Tela "Entregas" da empresa (V47): BDOs confirmados que ainda nao tem entrega - ninguem assumiu ainda, ou viajam
     * como bagagem carona de outra ainda nao entregue -, para a empresa poder cancelar o que chegou por engano. Os mais recentes primeiro,
     * limitados como a lista de entregas.
     */
    @Query(value = """
            SELECT b.* FROM bdo b
            WHERE b.status_conferencia = 'CONFIRMADO'
              AND NOT EXISTS (SELECT 1 FROM entrega e WHERE e.bdo_id = b.id)
              AND NOT EXISTS (
                  SELECT 1 FROM bagagem_carona bc JOIN entrega p ON p.id = bc.entrega_principal_id
                  WHERE bc.bdo_id = b.id AND p.status = 'ENCERRADA_COM_SUCESSO')
            ORDER BY b.data_captura DESC
            LIMIT 200
            """, nativeQuery = true)
    List<Bdo> findTop200SemEntrega();
}
