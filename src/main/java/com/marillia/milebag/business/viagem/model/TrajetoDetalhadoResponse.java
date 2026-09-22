package com.marillia.milebag.business.viagem.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ver contracts/viagem-api.md, respostas de GET .../viagem e
 * POST .../viagem/encerrar. Os campos finais ficam {@code null} enquanto
 * {@code status = EM_EXECUCAO}.
 */
public record TrajetoDetalhadoResponse(
        UUID id,
        TrajetoStatus status,
        Instant dataInicio,
        List<CalculoRotaResponse> calculosRota,
        List<CheckpointResponse> checkpoints,
        BigDecimal kmIdaFinal,
        BigDecimal kmVoltaFinal,
        List<TrechoPedagio> pracasPedagioFinal,
        Long tempoFinal,
        String justificativaDivergencia) {
}
