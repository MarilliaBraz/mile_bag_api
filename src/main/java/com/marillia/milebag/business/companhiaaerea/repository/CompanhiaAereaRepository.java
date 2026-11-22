package com.marillia.milebag.business.companhiaaerea.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.companhiaaerea.model.CompanhiaAerea;

/** Nao estende {@code BaseRepository}: lista de referencia, sem tenant (V53). */
public interface CompanhiaAereaRepository extends JpaRepository<CompanhiaAerea, String> {

    List<CompanhiaAerea> findAllByOrderByNomeAsc();
}
