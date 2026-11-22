package com.marillia.milebag.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class EnderecoComparavelTest {

    private static boolean mesmo(String a, String cidadeA, String b, String cidadeB) {
        Optional<EnderecoComparavel> x = EnderecoComparavel.de(a, cidadeA);
        Optional<EnderecoComparavel> y = EnderecoComparavel.de(b, cidadeB);
        return x.isPresent() && y.isPresent() && x.get().mesmoLocal(y.get());
    }

    private static boolean mesmo(String a, String b) {
        return mesmo(a, null, b, null);
    }

    @Test
    void abreviacaoAcentoEPontuacaoNaoImportam() {
        assertThat(mesmo("Av. Goiás, 100 - Centro", "AVENIDA GOIAS 100")).isTrue();
        assertThat(mesmo("R. das Flores, nº 25", "Rua das Flores 25")).isTrue();
    }

    @Test
    void numeroDiferenteNaoEOMesmoEndereco() {
        assertThat(mesmo("Av. Goiás, 100", "Av. Goiás, 102")).isFalse();
    }

    @Test
    void ruaDiferenteNaoEOMesmoEndereco() {
        assertThat(mesmo("Rua 10, 250", "Rua 11, 250")).isFalse();
        assertThat(mesmo("Av. T-63, 1200", "Av. T-62, 1200")).isFalse();
    }

    @Test
    void ruaComNumeroNoNomeComoEmGoiania() {
        assertThat(mesmo("Rua 10, 250, Setor Oeste", "R 10 n 250")).isTrue();
        assertThat(mesmo("Av. T-63, 1200", "AVENIDA T63, 1200 - Setor Bueno")).isTrue();
    }

    @Test
    void complementoNaoSeparaOEndereco() {
        assertThat(mesmo("Rua das Flores, 25, apto 302", "Rua das Flores 25 Bloco B")).isTrue();
    }

    @Test
    void quadraELoteContam() {
        assertThat(mesmo("Rua C-140, Qd. 10, Lt. 5, Jardim América", "RUA C140 QUADRA 10 LOTE 5")).isTrue();
        assertThat(mesmo("Rua C-140, Qd. 10, Lt. 5", "Rua C-140, Qd. 10, Lt. 6")).isFalse();
    }

    @Test
    void cidadesDiferentesNaoSaoOMesmoEndereco() {
        assertThat(mesmo("Rua 10, 250", "Goiânia", "Rua 10, 250", "Anápolis")).isFalse();
        assertThat(mesmo("Rua 10, 250", "Goiânia", "Rua 10, 250", "GOIANIA")).isTrue();
        // Sem cidade em um dos lados, vale a rua e o numero.
        assertThat(mesmo("Rua 10, 250", "Goiânia", "Rua 10, 250", null)).isTrue();
    }

    @Test
    void enderecoVagoNaoGeraComparacao() {
        assertThat(EnderecoComparavel.de(null, null)).isEmpty();
        assertThat(EnderecoComparavel.de("  ", null)).isEmpty();
        assertThat(EnderecoComparavel.de("Centro", null)).isEmpty();
        assertThat(EnderecoComparavel.de("Rua das Flores", null)).isEmpty();
    }

    @Test
    void semNumeroComQuadraELoteVale() {
        assertThat(mesmo("Rua 5, Qd 3, Lt 12, s/n", "Rua 5 Quadra 3 Lote 12")).isTrue();
    }

    @Test
    void cepNoTextoNaoAtrapalha() {
        assertThat(mesmo("Av. Goiás, 100, 74000-000", "Av Goiás 100")).isTrue();
    }

    private static boolean mesmoComCep(String a, String cepA, String b, String cepB) {
        Optional<EnderecoComparavel> x = EnderecoComparavel.de(a, "Goiânia", cepA);
        Optional<EnderecoComparavel> y = EnderecoComparavel.de(b, "Goiânia", cepB);
        return x.isPresent() && y.isPresent() && x.get().mesmoLocal(y.get());
    }

    @Test
    void cepsDiferentesSeparamRuasNumeradasDeSetoresDiferentes() {
        assertThat(mesmoComCep("Rua 10, 25", "74120-010", "Rua 10, 25", "74230-010")).isFalse();
        assertThat(mesmoComCep("Rua 10, 25", "74120010", "Rua 10, 25", "74120-010")).isTrue();
        // Sem CEP em um dos lados, vale a rua e o numero.
        assertThat(mesmoComCep("Rua 10, 25", "74120-010", "Rua 10, 25", null)).isTrue();
        // O CEP escrito no endereco tambem conta.
        assertThat(mesmoComCep("Rua 10, 25, 74120-010", null, "Rua 10, 25", "74230-010")).isFalse();
    }

    @Test
    void ruaDeUmaLetraNaoViraPreposicao() {
        assertThat(mesmo("Rua E, Qd 10, Lt 5", "Rua F, Qd 10, Lt 5")).isFalse();
        assertThat(mesmo("Rua E, 100", "R. E, nº 100")).isTrue();
        assertThat(mesmo("Rua E, 100", "Rua F, 100")).isFalse();
    }

    @Test
    void abreviacaoSoValeNoTipoDoLogradouro() {
        assertThat(mesmo("Rua R, 30", "Rua Rua, 30")).isFalse();
    }
}

