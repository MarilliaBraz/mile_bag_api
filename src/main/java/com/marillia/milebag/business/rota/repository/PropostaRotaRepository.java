package com.marillia.milebag.business.rota.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Serializa, por entrega, o "ler a proposta e regravar" de {@code
     * RotaService.calcular}/{@code selecionar}. Sem isso, dois calculos
     * simultaneos da mesma entrega (duplo toque, retry do cliente, ou o
     * StrictMode do React em desenvolvimento) leem "nao existe" ao mesmo
     * tempo e os dois tentam inserir a mesma chave primaria - o segundo
     * falha com 500. O lock e liberado sozinho no fim da transacao.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(cast(:entregaId as text)))) travado", nativeQuery = true)
    int travarPorEntrega(@Param("entregaId") UUID entregaId);
}
