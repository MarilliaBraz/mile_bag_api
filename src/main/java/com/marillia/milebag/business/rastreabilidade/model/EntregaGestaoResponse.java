package com.marillia.milebag.business.rastreabilidade.model;

import java.time.Instant;
import java.util.List;
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
        Instant atualizadoEm,
        /** V51: o motorista voltou e corrigiu alguma etapa (selo "Corrigida"; o detalhe esta no historico). */
        boolean corrigida,
        /** V52: codigo IATA da base do BDO (quem pode assumir). */
        String baseCodigo,
        /** Item 8: numeros dos outros BDOs em aberto que vao para o mesmo endereco (vazio se nenhum). */
        List<String> mesmoEnderecoQue) {
}
