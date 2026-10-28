package com.marillia.milebag.business.bagagemencontrada.repository;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.core.base.BaseRepository;

public interface BagagemEncontradaRepository extends BaseRepository<BagagemEncontrada, UUID> {

    /** Rascunhos do proprio funcionario, por conferir. */
    List<BagagemEncontrada> findByCriadoPorAndSituacaoOrderByCriadoEmDesc(UUID criadoPor, SituacaoBagagemEncontrada situacao);

    /** Os mais recentes da companhia (RLS), para o inicio do funcionario. */
    List<BagagemEncontrada> findTop20BySituacaoOrderByRegistradaEmDesc(SituacaoBagagemEncontrada situacao);
}
