package com.marillia.milebag.business.encaminhamento.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.encaminhamento.model.ParceriaEntrega;

/** Sem RLS (V45): toda consulta recebe a companhia, que vem do token ou da tela da plataforma. */
public interface ParceriaEntregaRepository extends JpaRepository<ParceriaEntrega, ParceriaEntrega.Chave> {

    List<ParceriaEntrega> findByCompanhiaId(UUID companhiaId);

    boolean existsByCompanhiaIdAndEmpresaId(UUID companhiaId, UUID empresaId);

    void deleteByCompanhiaId(UUID companhiaId);
}
