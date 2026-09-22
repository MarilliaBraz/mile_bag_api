package com.marillia.milebag.business.configuracao.model;

import java.util.UUID;

/**
 * Ver contracts/configuracoes-tenant-api.md. O unico GET do contrato lista
 * so os motivos ativos (consumido pelo app do motorista, HU07) - por isso
 * nao carrega {@code ativo} (sempre {@code true} nessa lista).
 */
public record MotivoOcorrenciaResponse(UUID id, String descricao) {

    public static MotivoOcorrenciaResponse de(MotivoOcorrencia motivo) {
        return new MotivoOcorrenciaResponse(motivo.getId(), motivo.getDescricao());
    }
}
