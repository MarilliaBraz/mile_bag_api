package com.marillia.milebag.business.dadosbdo.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValoresPassageiroTest {

    @Test
    void diferencasListamSoOQueMudouComOAntesEODepois() {
        ValoresPassageiro antes = new ValoresPassageiro("Ana", "Rua 1, Goiania - GO", null, "ana@exemplo.com");
        ValoresPassageiro depois = new ValoresPassageiro("Ana", "Rua 2, Goiania - GO", "62 99999-0000", "ana@exemplo.com");

        assertThat(antes.diferencasPara(depois))
                .isEqualTo("Endereco: Rua 1, Goiania - GO -> Rua 2, Goiania - GO; Telefone: - -> 62 99999-0000");
    }

    @Test
    void semDiferencaFicaVazio() {
        ValoresPassageiro valores = new ValoresPassageiro("Ana", "Rua 1", "62", "a@b.co");

        assertThat(valores.diferencasPara(valores)).isEmpty();
        assertThat(valores.mesmoQue(new ValoresPassageiro("Ana", "Rua 1", "62", "a@b.co"))).isTrue();
    }
}
