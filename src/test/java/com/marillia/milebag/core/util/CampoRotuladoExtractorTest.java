package com.marillia.milebag.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampoRotuladoExtractorTest {

    @Test
    void casaRotuloSemDiferenciarMaiusculasNemAcentos() {
        assertThat(CampoRotuladoExtractor.extrair("ENDERECO: Rua 20, 100", "Endereço")).isEqualTo("Rua 20, 100");
        assertThat(CampoRotuladoExtractor.extrair("endereço: Rua 20, 100", "ENDERECO")).isEqualTo("Rua 20, 100");
    }

    @Test
    void casaRotuloDeVariasPalavrasComEspacosVariados() {
        assertThat(CampoRotuladoExtractor.extrair("Nome  do passageiro:  Ana Souza", "NOME DO PASSAGEIRO")).isEqualTo("Ana Souza");
    }

    @Test
    void preservaAcentosDoValor() {
        assertThat(CampoRotuladoExtractor.extrair("ADDR: Av. Goiás, Goiânia", "ADDR")).isEqualTo("Av. Goiás, Goiânia");
    }

    /** "TEL" nao pode casar o comeco de "TELEFONE" nem "NOME" o comeco de "Nome do passageiro". */
    @Test
    void rotuloCurtoNaoCasaPrefixoDeOutraPalavra() {
        assertThat(CampoRotuladoExtractor.extrair("TELEFONE: 62999990000", "TEL")).isNull();
        assertThat(CampoRotuladoExtractor.extrair("Nome do passageiro: Ana", "NOME")).isNull();
    }

    @Test
    void usaOPrimeiroRotuloEncontradoNaOrdemRecebida() {
        String texto = "Nome: Rotulo generico\nNome do passageiro: Ana Souza";
        assertThat(CampoRotuladoExtractor.extrair(texto, "NOME DO PASSAGEIRO", "NOME")).isEqualTo("Ana Souza");
    }

    @Test
    void retornaNuloSemRotuloOuSemTexto() {
        assertThat(CampoRotuladoExtractor.extrair("PAX: Ana", "ADDR")).isNull();
        assertThat(CampoRotuladoExtractor.extrair(null, "ADDR")).isNull();
    }
}
