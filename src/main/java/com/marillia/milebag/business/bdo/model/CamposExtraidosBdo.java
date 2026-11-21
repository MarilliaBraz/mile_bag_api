package com.marillia.milebag.business.bdo.model;

import java.time.LocalDate;

/**
 * Campos de um BDO: lidos do telex WorldTracer ({@code LeitorTelexBdo}, e-mail ou texto colado pela empresa),
 * conferidos pela empresa no formulario de inclusao ou montados pela companhia aerea no encaminhamento.
 *
 * <p>{@code enderecoEntrega} e a linha do logradouro; cidade, UF e CEP vem separados e o {@code BdoService} monta o
 * endereco completo usado na rota. {@code etiquetaBagagem} e {@code coresBagagem} trazem uma ou mais malas separadas
 * por virgula.</p>
 */
public record CamposExtraidosBdo(
        String numero,
        String passageiroNome,
        String enderecoEntrega,
        String companhiaAerea,
        String telefonePassageiro,
        String emailPassageiro,
        String etiquetaBagagem,
        String tipoProcesso,
        String cidade,
        String uf,
        String cep,
        String instrucoesEntrega,
        String aeroportoRetirada,
        String vooChegada,
        LocalDate dataEntregaPrevista,
        Integer quantidadeMalas,
        String coresBagagem,
        String valorInformado) {

    /** Encaminhamento da companhia aerea (HU37): so os campos basicos, o endereco ja vem completo. */
    public CamposExtraidosBdo(String numero, String passageiroNome, String enderecoEntrega, String companhiaAerea,
            String telefonePassageiro, String emailPassageiro, String etiquetaBagagem) {
        this(numero, passageiroNome, enderecoEntrega, companhiaAerea, telefonePassageiro, emailPassageiro, etiquetaBagagem,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
