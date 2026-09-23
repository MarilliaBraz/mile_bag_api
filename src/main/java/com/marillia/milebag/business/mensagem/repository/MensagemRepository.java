package com.marillia.milebag.business.mensagem.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.mensagem.model.Mensagem;
import com.marillia.milebag.core.base.BaseRepository;

/** Sem metodo de update exposto (append-only): {@link Mensagem} nao tem setters. */
public interface MensagemRepository extends BaseRepository<Mensagem, UUID> {

    List<Mensagem> findByEntregaIdOrderByCriadoEmAsc(UUID entregaId);
}
