package com.marillia.milebag.business.bdo.model;

/**
 * Campos do padrao WorldTracer extraidos por OCR na captura (FR-002) e
 * corrigidos/confirmados pelo motorista na tela de conferencia (FR-004).
 * Mesmo formato usado como saida do OCR, corpo do PATCH de conferencia e
 * parte de {@link BdoResponse} - ver data-model.md e contracts/bdo-api.md.
 */
public record CamposExtraidosBdo(
        String numero,
        String passageiroNome,
        String enderecoEntrega,
        String companhiaAerea,
        String telefonePassageiro,
        String emailPassageiro,
        String etiquetaBagagem) {
}
