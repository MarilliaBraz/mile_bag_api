package com.marillia.milebag.business.entrega.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalhe completo de uma entrega (HU19, FR-003) - superset de {@link
 * ItemPainelResponse}: alem do resumo do painel, inclui os demais campos
 * extraidos do BDO relevantes ao motorista em campo (contracts/painel-api.md),
 * como {@code telefone} (usado por HU21/HU18) e {@code companhiaAerea}.
 */
public record ItemPainelDetalheResponse(
        UUID entregaId,
        EntregaStatus status,
        String bdoNumero,
        String passageiroNome,
        String endereco,
        String companhiaAerea,
        String telefone,
        String etiquetaBagagem,
        List<BagagemCaronaResumo> bagagensCarona,
        Instant dataAutoatribuicao,
        boolean temTrajetoIniciado,
        String marcoMaisRecente) {
}
