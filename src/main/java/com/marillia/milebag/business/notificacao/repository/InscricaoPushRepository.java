package com.marillia.milebag.business.notificacao.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.notificacao.model.InscricaoPush;
import com.marillia.milebag.core.base.BaseRepository;

public interface InscricaoPushRepository extends BaseRepository<InscricaoPush, UUID> {

    Optional<InscricaoPush> findByEndpoint(String endpoint);

    List<InscricaoPush> findByUsuarioIdIn(Collection<UUID> usuarioIds);
}
