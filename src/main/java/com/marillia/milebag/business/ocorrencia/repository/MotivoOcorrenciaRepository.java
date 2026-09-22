package com.marillia.milebag.business.ocorrencia.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.ocorrencia.model.MotivoOcorrencia;

/**
 * Somente leitura nesta HU (T006) - isolamento por tenant garantido pela
 * politica de Row Level Security da tabela {@code motivo_ocorrencia}, como
 * em todo repositorio deste projeto.
 */
public interface MotivoOcorrenciaRepository extends JpaRepository<MotivoOcorrencia, UUID> {

    List<MotivoOcorrencia> findByAtivoTrue();
}
