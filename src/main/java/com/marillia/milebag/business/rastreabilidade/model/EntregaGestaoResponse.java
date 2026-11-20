package com.marillia.milebag.business.rastreabilidade.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.business.entrega.model.EntregaStatus;

/**
 * Uma linha da tela "Entregas" da empresa (V46). {@code motoristaNome} nulo: entrega sem motorista no momento.
 * V47: tambem o BDO que ninguem assumiu ainda - {@code entregaId} nulo e status {@code AGUARDANDO_ATRIBUICAO} -, para a
 * empresa poder cancela-lo; {@code bagagemCarona}: esse BDO viaja junto de outra entrega.
 */
public record EntregaGestaoResponse(
        UUID entregaId,
        UUID bdoId,
        boolean bagagemCarona,
        EntregaStatus status,
        String bdoNumero,
        String passageiroNome,
        String endereco,
        String motoristaNome,
        Instant atualizadoEm) {
}
