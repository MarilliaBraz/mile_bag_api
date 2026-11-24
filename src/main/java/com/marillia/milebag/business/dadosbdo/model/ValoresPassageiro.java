package com.marillia.milebag.business.dadosbdo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Os dados do passageiro como ficam gravados no BDO (endereco ja completo) - o antes ou o depois de uma correcao. */
public record ValoresPassageiro(String nome, String endereco, String telefone, String email) {

    /** "Endereco: antigo -> novo; Telefone: - -> 62 99999-0000": so os campos que mudaram, para o historico da mala. */
    public String diferencasPara(ValoresPassageiro novo) {
        List<String> mudancas = new ArrayList<>();
        adicionar(mudancas, "Nome", nome, novo.nome);
        adicionar(mudancas, "Endereco", endereco, novo.endereco);
        adicionar(mudancas, "Telefone", telefone, novo.telefone);
        adicionar(mudancas, "E-mail", email, novo.email);
        return String.join("; ", mudancas);
    }

    public boolean mesmoQue(ValoresPassageiro outro) {
        return Objects.equals(nome, outro.nome) && Objects.equals(endereco, outro.endereco)
                && Objects.equals(telefone, outro.telefone) && Objects.equals(email, outro.email);
    }

    private static void adicionar(List<String> mudancas, String campo, String antes, String depois) {
        if (!Objects.equals(antes, depois)) {
            mudancas.add(campo + ": " + (antes == null ? "-" : antes) + " -> " + (depois == null ? "-" : depois));
        }
    }
}
