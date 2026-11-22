package com.marillia.milebag.business.viagem.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrajetoStatus;
import com.marillia.milebag.core.base.BaseRepository;

/**
 * Sem metodo de atualizacao alem de {@code save()} sobre uma entidade ja
 * carregada - a imutabilidade por campo e garantida pela ausencia de
 * setters em {@link Trajeto} (so {@link Trajeto#consolidar} muda estado).
 */
public interface TrajetoRepository extends BaseRepository<Trajeto, UUID> {

    /** A viagem mais recente da entrega - a da tentativa atual, a menos que uma ocorrencia a tenha encerrado (ver ViagemService). */
    Optional<Trajeto> findFirstByEntregaIdOrderByDataInicioDesc(UUID entregaId);

    /** Viagens de tentativas sem sucesso (ocorrencia ou dispensa em rota) encerradas no periodo - pagas a quem dirigiu. */
    @Query("""
            SELECT t FROM Trajeto t
            WHERE (t.ocorrenciaId IS NOT NULL OR t.dispensaId IS NOT NULL)
              AND t.encerradoEm >= :inicio AND t.encerradoEm < :fim
            """)
    List<Trajeto> listarTentativasSemSucessoEncerradasEntre(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    List<Trajeto> findByEntregaIdOrderByDataInicioAsc(UUID entregaId);

    /**
     * Item 9: as viagens da entrega travadas para escrita - dois lotes de pontos da mesma viagem entram um de cada vez,
     * e o km recalculado por um ve os pontos do outro.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trajeto t WHERE t.entregaId = :entregaId ORDER BY t.dataInicio")
    List<Trajeto> findByEntregaIdParaAtualizar(@Param("entregaId") UUID entregaId);

    /** Se a entrega ja teve alguma viagem (de qualquer tentativa): a proxima dispensa exige motivo. */
    boolean existsByEntregaId(UUID entregaId);

    /** HU31: a viagem em andamento do motorista, que recebe a posicao enviada pelo app. */
    Optional<Trajeto> findFirstByMotoristaIdAndStatusOrderByDataInicioDesc(UUID motoristaId, TrajetoStatus status);
}
