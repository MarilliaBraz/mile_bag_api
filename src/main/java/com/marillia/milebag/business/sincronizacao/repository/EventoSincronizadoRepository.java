package com.marillia.milebag.business.sincronizacao.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.sincronizacao.model.EventoSincronizado;

/** {@code findById(idLocal)} e a propria checagem de deduplicacao (FR-006). */
public interface EventoSincronizadoRepository extends JpaRepository<EventoSincronizado, UUID> {
}
