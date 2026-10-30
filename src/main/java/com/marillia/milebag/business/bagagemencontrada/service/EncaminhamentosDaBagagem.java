package com.marillia.milebag.business.bagagemencontrada.service;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;

/**
 * O encaminhamento ativo de uma bagagem (HU37), para a ficha, a consulta e o CSV. Implementado no dominio
 * {@code encaminhamento}, que depende deste - assim este dominio nao depende dele.
 */
public interface EncaminhamentosDaBagagem {

    Optional<BagagemEncontradaResponse.Encaminhamento> ativo(UUID bagagemId);
}
