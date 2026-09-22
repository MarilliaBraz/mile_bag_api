package com.marillia.milebag.business.configuracao.model;

/**
 * Catalogo fixo do sistema (Assumptions do spec.md desta HU: "o tenant
 * escolhe quais, dentre esses, sao obrigatorios - nao cria campos novos").
 * RI10 escopa "baixa" a dois fluxos ja implementados: comprovante de
 * entrega (HU06) e ocorrencia (HU07). Os campos do POD (foto,
 * identificacao, data/hora, geolocalizacao) ja sao obrigatorios por RN04
 * hoje (BdoService/ComprovanteEntregaService), independente desta
 * configuracao - {@code obrigatorioPadrao = true} reflete isso (data-model.md:
 * "Default conforme o campo (ex.: campos do POD ja obrigatorios por RN04").
 * A descricao adicional da ocorrencia e o unico campo genuinamente opcional
 * hoje (HU07), por isso e o unico com {@code obrigatorioPadrao = false}.
 */
public enum CampoBaixa {
    POD_FOTO_DOCUMENTO(true),
    POD_IDENTIFICACAO_RECEBEDOR(true),
    POD_DATA_HORA(true),
    POD_GEOLOCALIZACAO(true),
    OCORRENCIA_DESCRICAO_ADICIONAL(false);

    private final boolean obrigatorioPadrao;

    CampoBaixa(boolean obrigatorioPadrao) {
        this.obrigatorioPadrao = obrigatorioPadrao;
    }

    public boolean isObrigatorioPadrao() {
        return obrigatorioPadrao;
    }
}
