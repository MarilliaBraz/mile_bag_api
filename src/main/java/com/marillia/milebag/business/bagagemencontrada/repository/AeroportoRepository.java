package com.marillia.milebag.business.bagagemencontrada.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;

/** Nao estende {@code BaseRepository}: {@link Aeroporto} e referencia nacional, sem tenant (V41). */
public interface AeroportoRepository extends JpaRepository<Aeroporto, String> {

    List<Aeroporto> findAllByOrderByCodigoAsc();
}
