package com.marillia.milebag.business.bagagemencontrada.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaNacional;

/** Busca nacional por etiqueta (HU36, RD22): todas as companhias, sem RLS. */
public interface BagagemEncontradaNacionalRepository extends JpaRepository<BagagemEncontradaNacional, UUID> {

    List<BagagemEncontradaNacional> findTop50ByNumeroEtiquetaOrderByRegistradaEmDesc(String numeroEtiqueta);
}
