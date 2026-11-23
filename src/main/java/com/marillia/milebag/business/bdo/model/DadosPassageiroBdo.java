package com.marillia.milebag.business.bdo.model;

/**
 * Os dados do passageiro que a empresa pode corrigir num BDO ja registrado: nome, endereco, telefone e e-mail.
 * {@code enderecoEntrega} e so o logradouro - cidade, UF e CEP vem separados, como na inclusao, e o {@code BdoService}
 * monta o endereco completo usado na rota.
 */
public record DadosPassageiroBdo(
        String passageiroNome,
        String enderecoEntrega,
        String cidade,
        String uf,
        String cep,
        String telefonePassageiro,
        String emailPassageiro) {
}
