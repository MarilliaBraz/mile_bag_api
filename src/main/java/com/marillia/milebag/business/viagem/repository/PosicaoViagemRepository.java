package com.marillia.milebag.business.viagem.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.marillia.milebag.business.viagem.model.PosicaoViagem;
import com.marillia.milebag.core.base.BaseRepository;

public interface PosicaoViagemRepository extends BaseRepository<PosicaoViagem, UUID> {

    List<PosicaoViagem> findByTrajetoIdOrderByDataHoraAsc(UUID trajetoId);

    /** Os pontos deste lote que ja entraram antes (reenvio do app) - so nas viagens do proprio motorista. */
    @Query("SELECT p.idLocal FROM PosicaoViagem p WHERE p.idLocal IN :idsLocais AND p.trajetoId IN :viagens")
    List<String> idsLocaisJaRegistrados(@Param("idsLocais") Collection<String> idsLocais, @Param("viagens") Collection<UUID> viagens);
}
