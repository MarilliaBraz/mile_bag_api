package com.marillia.milebag.business.viagem.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Uma viagem paga do relatorio quinzenal do motorista (HU17) - entrega concluida ou tentativa sem sucesso -, com os valores atualmente vigentes. */
public record ItemRelatorioMotoristaResponse(
        UUID entregaId,
        /** Id do Trajeto: e o {@code viagemId} das rotas de ajuste, ja no relatorio para o cliente nao precisar de outra chamada. */
        UUID viagemId,
        String bdoNumero,
        BigDecimal kmIda,
        BigDecimal kmVolta,
        BigDecimal tarifaAplicada,
        BigDecimal valorPedagio,
        boolean temAjuste,
        /** Viagem de uma tentativa sem sucesso (HU07): paga ao motorista que dirigiu, mesmo sem a entrega concluida. */
        boolean tentativaSemSucesso) {
}
