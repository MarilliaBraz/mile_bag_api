package com.marillia.milebag.business.configuracao.model;

import java.util.UUID;

/**
 * Ver contracts/configuracoes-tenant-api.md. O GET lista so os motivos ativos de uma finalidade (ocorrencia, por
 * padrao, ou dispensa - V46), consumido pelo app do motorista e pelas configuracoes - por isso nao carrega
 * {@code ativo} (sempre {@code true} nessa lista).
 */
public record MotivoOcorrenciaResponse(UUID id, String descricao, boolean reagendamento, FinalidadeMotivo finalidade) {

    public static MotivoOcorrenciaResponse de(MotivoOcorrencia motivo) {
        return new MotivoOcorrenciaResponse(motivo.getId(), motivo.getDescricao(), motivo.isReagendamento(), motivo.getFinalidade());
    }
}
