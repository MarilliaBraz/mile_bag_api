package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLeitura;

class EtiquetaTextoParserTest {

    private static final Set<String> AEROPORTOS = Set.of("GRU", "GYN", "BSB");

    @Test
    void leOsCamposDeUmaEtiquetaTipica() {
        String texto = """
                LATAM AIRLINES
                GRU
                LA 3456 / 28OCT
                SILVA/MARIA
                LA 123456
                0 957 123456
                """;

        LeituraEtiqueta lida = EtiquetaTextoParser.extrair(texto, AEROPORTOS);

        assertThat(lida.numeroEtiqueta()).isEqualTo("0957123456");
        assertThat(lida.companhia()).isEqualTo("LA");
        assertThat(lida.destino()).isEqualTo("GRU");
        assertThat(lida.voo()).isEqualTo("LA3456");
        assertThat(lida.nomePassageiro()).isEqualTo("SILVA/MARIA");
        assertThat(lida.origem()).isEqualTo(OrigemLeitura.TEXTO);
    }

    @Test
    void semOsDezDigitosUsaAPlaca() {
        LeituraEtiqueta lida = EtiquetaTextoParser.extrair("G3 654321\nBSB", AEROPORTOS);

        assertThat(lida.numeroEtiqueta()).isEqualTo("G3654321");
        assertThat(lida.companhia()).isEqualTo("G3");
        assertThat(lida.destino()).isEqualTo("BSB");
    }

    /** Palavras de 3 letras que nao sao aeroporto conhecido (LAT, AIR, BAG) nao viram destino. */
    @Test
    void soCodigoDeAeroportoConhecidoViraDestino() {
        assertThat(EtiquetaTextoParser.extrair("LAT AIR BAG GYN", AEROPORTOS).destino()).isEqualTo("GYN");
        assertThat(EtiquetaTextoParser.extrair("LAT AIR BAG", AEROPORTOS).destino()).isNull();
    }

    @Test
    void textoIlegivelNaoLeNada() {
        LeituraEtiqueta lida = EtiquetaTextoParser.extrair("~~ ## ..", AEROPORTOS);

        assertThat(lida.numeroEtiqueta()).isNull();
        assertThat(lida.origem()).isEqualTo(OrigemLeitura.NENHUMA);
        assertThat(EtiquetaTextoParser.extrair(null, AEROPORTOS).origem()).isEqualTo(OrigemLeitura.NENHUMA);
    }

    @Test
    void acentosEMinusculasNaoAtrapalham() {
        assertThat(EtiquetaTextoParser.extrair("joão/conceição", AEROPORTOS).nomePassageiro()).isEqualTo("JOAO/CONCEICAO");
    }
}
