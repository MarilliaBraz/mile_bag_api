package com.marillia.milebag.business.mensagem.model;

import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Liga o {@code chatId} do Telegram a uma Entrega, criado no primeiro
 * {@code /start <entregaId>} recebido pelo webhook do bot (HU18, FR-003a) -
 * ver data-model.md desta HU. No maximo um por entrega (constraint unica na
 * migracao); um mesmo {@code chatId} pode se repetir em entregas diferentes
 * do mesmo passageiro.
 */
@Entity
@Table(name = "vinculo_telegram")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VinculoTelegram extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private Long chatId;

    public VinculoTelegram(UUID entregaId, Long chatId) {
        this.entregaId = entregaId;
        this.chatId = chatId;
    }
}
