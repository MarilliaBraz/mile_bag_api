package com.marillia.milebag.business.mensagem.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.mensagem.model.VinculoTelegram;
import com.marillia.milebag.core.base.BaseRepository;

public interface VinculoTelegramRepository extends BaseRepository<VinculoTelegram, UUID> {

    Optional<VinculoTelegram> findByEntregaId(UUID entregaId);

    boolean existsByEntregaId(UUID entregaId);

    /** Um {@code chatId} pode se repetir em entregas diferentes do mesmo passageiro (data-model.md). */
    List<VinculoTelegram> findByChatId(Long chatId);
}
