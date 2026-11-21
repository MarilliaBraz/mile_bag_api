package com.marillia.milebag.business.correcao.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.correcao.model.CorrecaoEntrega;
import com.marillia.milebag.core.base.BaseRepository;

/** RLS de correcao_entrega (V51) isola por tenant. */
public interface CorrecaoEntregaRepository extends BaseRepository<CorrecaoEntrega, UUID> {

    List<CorrecaoEntrega> findByEntregaIdOrderByDataHoraAsc(UUID entregaId);

    /** Entregas da lista que tem alguma correcao - o selo "Corrigida" da tela Entregas, numa consulta so. */
    @Query("select distinct c.entregaId from CorrecaoEntrega c where c.entregaId in :entregas")
    List<UUID> entregasCorrigidas(@Param("entregas") Collection<UUID> entregas);
}
