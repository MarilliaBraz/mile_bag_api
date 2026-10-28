package com.marillia.milebag.business.viagem.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /** Viagens de tentativas sem sucesso encerradas no periodo (pagas no fechamento a quem dirigiu). */
    List<Trajeto> findByOcorrenciaIdIsNotNullAndEncerradoEmGreaterThanEqualAndEncerradoEmLessThan(Instant inicio, Instant fim);

    /** HU31: a viagem em andamento do motorista, que recebe a posicao enviada pelo app. */
    Optional<Trajeto> findFirstByMotoristaIdAndStatusOrderByDataInicioDesc(UUID motoristaId, TrajetoStatus status);
}
