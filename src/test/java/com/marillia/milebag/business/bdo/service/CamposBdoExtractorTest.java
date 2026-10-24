package com.marillia.milebag.business.bdo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;

class CamposBdoExtractorTest {

    @Test
    void extraiRotulosDoWorldTracer() {
        CamposExtraidosBdo campos = CamposBdoExtractor.extrair("""
                BDO: GYNAZ90001
                PAX: PASSAGEIRO FICTICIO
                ADDR: RUA 20, 100, SETOR CENTRAL, GOIANIA - GO
                CIA: AZUL
                TEL: 62900000001
                EMAIL: passageiro@milebag.local
                TAG: AD900011
                """);

        assertThat(campos).isEqualTo(new CamposExtraidosBdo("GYNAZ90001", "PASSAGEIRO FICTICIO",
                "RUA 20, 100, SETOR CENTRAL, GOIANIA - GO", "AZUL", "62900000001", "passageiro@milebag.local", "AD900011"));
    }

    /** Formulario em portugues (o caso que falhava no teste em homologacao de 24/10/2026). */
    @Test
    void extraiRotulosDeFormularioEmPortugues() {
        CamposExtraidosBdo campos = CamposBdoExtractor.extrair("""
                BOLETIM DE OCORRENCIA DE BAGAGEM (BDO)
                Número do BDO: GYNAZ90001
                Companhia aérea: AZUL
                Etiqueta da bagagem: AD900011
                Nome do passageiro: Passageiro Fictício
                Telefone: (62) 90000-0001
                E-mail: passageiro@milebag.local
                Endereço de entrega: Rua 20, 100, Setor Central, Goiânia - GO
                """);

        assertThat(campos).isEqualTo(new CamposExtraidosBdo("GYNAZ90001", "Passageiro Fictício",
                "Rua 20, 100, Setor Central, Goiânia - GO", "AZUL", "(62) 90000-0001", "passageiro@milebag.local", "AD900011"));
    }

    /** O OCR costuma perder acentos e cedilha e devolver tudo em maiusculas. */
    @Test
    void extraiRotulosEmPortuguesSemAcentoEmMaiusculas() {
        CamposExtraidosBdo campos = CamposBdoExtractor.extrair("""
                NUMERO DO BDO: GYNAZ90001
                NOME DO PASSAGEIRO: PASSAGEIRO FICTICIO
                ENDERECO DE ENTREGA: RUA 20, 100
                CIA AEREA: AZUL
                """);

        assertThat(campos.numero()).isEqualTo("GYNAZ90001");
        assertThat(campos.passageiroNome()).isEqualTo("PASSAGEIRO FICTICIO");
        assertThat(campos.enderecoEntrega()).isEqualTo("RUA 20, 100");
        assertThat(campos.companhiaAerea()).isEqualTo("AZUL");
    }
}
