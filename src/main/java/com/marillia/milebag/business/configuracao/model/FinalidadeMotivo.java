package com.marillia.milebag.business.configuracao.model;

/**
 * Para que serve um motivo do catalogo da empresa: registrar uma ocorrencia de nao entrega (HU07) ou justificar a
 * dispensa de uma entrega pelo motorista (V46). As duas listas vivem no mesmo cadastro, cada uma com os seus itens.
 */
public enum FinalidadeMotivo {
    OCORRENCIA,
    DISPENSA
}
