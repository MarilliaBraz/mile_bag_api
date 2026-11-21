package com.marillia.milebag.business.configuracao.model;

/**
 * Para que serve um motivo do catalogo da empresa: registrar uma ocorrencia de nao entrega (HU07) ou justificar a
 * dispensa de uma entrega pelo motorista (V46) ou o cancelamento de um BDO pela empresa (V47). As listas vivem no
 * mesmo cadastro, cada uma com os seus itens.
 */
public enum FinalidadeMotivo {
    OCORRENCIA,
    DISPENSA,
    CANCELAMENTO,
    /** V48: quem pode assinar o recebimento (titular, familiar, portaria...), escolhido no comprovante. */
    RECEBEDOR,
    /** V51: motivo de voltar e corrigir uma etapa da entrega (auditoria). */
    CORRECAO
}
