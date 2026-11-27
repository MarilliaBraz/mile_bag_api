package com.marillia.milebag.business.viagem.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.viagem.model.LinkAcompanhamento;

/** Nao estende {@code BaseRepository}: {@link LinkAcompanhamento} nao e {@code BaseEntity} (sem RLS - ver V38). */
public interface LinkAcompanhamentoRepository extends JpaRepository<LinkAcompanhamento, UUID> {

    /** V65: o link mais recente da entrega, para a pesquisa de satisfacao depois da entrega. */
    Optional<LinkAcompanhamento> findFirstByEntregaIdOrderByCriadoEmDesc(UUID entregaId);
}
