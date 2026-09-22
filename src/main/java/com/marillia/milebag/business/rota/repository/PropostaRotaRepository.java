package com.marillia.milebag.business.rota.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.rota.model.PropostaRota;

/**
 * {@code save()} funciona como upsert por {@code entregaId} (chave primaria
 * atribuida manualmente, sem {@code @GeneratedValue}): o Spring Data JPA
 * chama {@code merge()} em vez de {@code persist()} quando o id ja vem
 * preenchido - exatamente o comportamento exigido pelo Edge Case do
 * spec.md (cada novo calculo substitui a proposta anterior da mesma
 * entrega).
 */
public interface PropostaRotaRepository extends JpaRepository<PropostaRota, UUID> {
}
