package com.marillia.milebag.business.acompanhamento.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.acompanhamento.model.AvaliacaoEntrega;

/** Sem RLS (V65): a pagina publica grava pelo link; quem le filtra pela entrega ou pelo BDO. */
public interface AvaliacaoEntregaRepository extends JpaRepository<AvaliacaoEntrega, UUID> {

    boolean existsByEntregaId(UUID entregaId);
}
