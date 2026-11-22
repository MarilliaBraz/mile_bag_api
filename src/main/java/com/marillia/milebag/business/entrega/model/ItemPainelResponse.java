package com.marillia.milebag.business.entrega.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Uma entrega pendente na lista do painel do motorista (HU19) - ver contracts/painel-api.md e data-model.md. */
public record ItemPainelResponse(
        UUID entregaId,
        EntregaStatus status,
        String bdoNumero,
        String passageiroNome,
        String endereco,
        String etiquetaBagagem,
        List<BagagemCaronaResumo> bagagensCarona,
        Instant dataAutoatribuicao,
        boolean temTrajetoIniciado,
        String marcoMaisRecente,
        JanelaReagendamento janelaReagendamento,
        // V48: o motorista ja registrou a retirada da bagagem desta tentativa (proxima etapa: iniciar a viagem).
        boolean retiradaRegistrada,
        // Item 8: quantas malas o BDO tem (nulo se o BDO nao informa).
        Integer quantidadeMalas) {
}
