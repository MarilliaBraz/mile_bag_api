package com.marillia.milebag.business.configuracao.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;

public interface MotivoOcorrenciaRepository extends JpaRepository<MotivoOcorrencia, UUID> {

    List<MotivoOcorrencia> findByAtivoTrue();
}
