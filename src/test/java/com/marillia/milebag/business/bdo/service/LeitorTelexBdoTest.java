package com.marillia.milebag.business.bdo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;

/**
 * Formatos tirados de BDOs reais (V49), com todos os dados pessoais trocados por ficticios: o telex WorldTracer em
 * suas variacoes de impressao e o formulario impresso de uma companhia.
 */
class LeitorTelexBdoTest {

    private final LeitorTelexBdo leitor =
            new LeitorTelexBdo(Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    void telexComEnderecoPermanenteDuasMalasEValor() {
        CamposExtraidosBdo bdo = leitor.ler("""
                                 WM BDO AHL GYNLA10001/0013GMT/16SEP26
                DS GYNLA03 - PACK AND GO - EXPRESSA
                LATAM AIRLINES GROUP...............................
                ...................................................
                NM01 FICTICIA
                GN01 MARIA
                PA AV EXEMPLO 100 APT 1 JD TESTE EDF MODELO
                YP GOIANIA
                ST GO
                CO BR
                ZP 74000100 .DD 15SEP .DW   .CS D/BRL132
                PN01 55 62 9 0000 0001
                EA01 MARIA.TESTE@EXEMPLO.COM
                LD01 TO PERMANENT ADDRESS/
                TN01 LA000001 .TN02 LA000002
                CT01 GN22 .CT02 BK22
                FD LA3266/15SEP
                RT GRU/GYN
                /RECHARGE -/SIGNATURE FOR RECEIPT OF 2 BAG
                SE BDO@EXEMPLO.COM.BR
                TX   .AG 0000001
                """);

        assertThat(bdo.numero()).isEqualTo("GYNLA10001");
        assertThat(bdo.tipoProcesso()).isEqualTo("AHL");
        assertThat(bdo.passageiroNome()).isEqualTo("MARIA FICTICIA");
        assertThat(bdo.enderecoEntrega()).isEqualTo("AV EXEMPLO 100 APT 1 JD TESTE EDF MODELO");
        assertThat(bdo.cidade()).isEqualTo("GOIANIA");
        assertThat(bdo.uf()).isEqualTo("GO");
        assertThat(bdo.cep()).isEqualTo("74000-100");
        assertThat(bdo.companhiaAerea()).isEqualTo("LATAM");
        assertThat(bdo.telefonePassageiro()).isEqualTo("55 62 9 0000 0001");
        assertThat(bdo.emailPassageiro()).isEqualTo("maria.teste@exemplo.com");
        assertThat(bdo.etiquetaBagagem()).isEqualTo("LA000001, LA000002");
        assertThat(bdo.coresBagagem()).isEqualTo("GN22, BK22");
        assertThat(bdo.quantidadeMalas()).isEqualTo(2);
        assertThat(bdo.dataEntregaPrevista()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(bdo.valorInformado()).isEqualTo("BRL 132");
        assertThat(bdo.instrucoesEntrega()).isEqualTo("TO PERMANENT ADDRESS");
        assertThat(bdo.aeroportoRetirada()).isEqualTo("GYN");
        assertThat(bdo.vooChegada()).isNull();
    }

    /** Impressao com "·" no lugar do espaco, "»" no inicio e o voo que traz a mala na instrucao de entrega. */
    @Test
    void telexImpressoComPontosEVooNaInstrucao() {
        CamposExtraidosBdo bdo = leitor.ler("""
                »WM BDO AHL GRUIB30001                         /-OK-/04NOV26 0821GMT
                DS GRUIB01 - PACK AND GO (+5511) 900000000
                UP01 20:00/DDMMM
                OF01 12:00 - 22:00/DDMM
                NM01·SOBRENOMETESTE
                PA·AV T1 2506 LOJA 3 GALERIA TESTE SETOR BUENO
                YP·GOIANIA                       .ST·GO
                ZP·74215022  .DD·04NOV .DW·01
                CP01·5562900000001
                EA01 FULANA/U/TESTE/A/HOTMAIL/D/COM
                LD01·LA3266  GYN  11:35 - 13:15
                LD02 ENDERECO COMERCIAL
                TN01 IB000001 .TN02 IB000002
                CT01 BK22 .CT02 WT22
                 /RECHARGE -·  /SIGNATURE FOR RECEIPT OF 2 BAGS    .RT·ORY
                SE BDO/A/EXEMPLO/D/COM/D/BR
                FECHA:04/11/026- HORA:08:20GMT - PID00001 -AGENTE0001
                """);

        assertThat(bdo.numero()).isEqualTo("GRUIB30001");
        assertThat(bdo.passageiroNome()).isEqualTo("SOBRENOMETESTE");
        assertThat(bdo.enderecoEntrega()).isEqualTo("AV T1 2506 LOJA 3 GALERIA TESTE SETOR BUENO");
        assertThat(bdo.cidade()).isEqualTo("GOIANIA");
        assertThat(bdo.uf()).isEqualTo("GO");
        assertThat(bdo.cep()).isEqualTo("74215-022");
        assertThat(bdo.companhiaAerea()).isEqualTo("IBERIA");
        assertThat(bdo.telefonePassageiro()).isEqualTo("5562900000001");
        assertThat(bdo.emailPassageiro()).isEqualTo("fulana_teste@hotmail.com");
        assertThat(bdo.etiquetaBagagem()).isEqualTo("IB000001, IB000002");
        assertThat(bdo.coresBagagem()).isEqualTo("BK22, WT22");
        assertThat(bdo.quantidadeMalas()).isEqualTo(2);
        assertThat(bdo.dataEntregaPrevista()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(bdo.instrucoesEntrega()).isEqualTo("LA3266 GYN 11:35 - 13:15 | ENDERECO COMERCIAL");
        assertThat(bdo.aeroportoRetirada()).isEqualTo("GYN");
        assertThat(bdo.vooChegada()).isEqualTo("LA3266");
    }

    /** Endereco temporario (TA/YT/TS/TZ) vale no lugar do permanente; o estado por extenso vira UF. */
    @Test
    void telexComEnderecoTemporarioEVooNaInformacaoEspecial() {
        CamposExtraidosBdo bdo = leitor.ler("""
                WM BDO AHL GRULX10001/1157GMT/14NOV26
                DS GRULHPGA - PACK & GO STANDARD FOR LX
                BAGGAGE DELIVERY/ LOST AND FOUND GUARULHOS..........
                NM01 DETESTE .NM02 EXEMPLO
                GN01 ANA
                TA R 24 N 320 APTO 1802 QD J11 LT 6/10 SETOR MARISTA
                YT GOIANIA
                TS GOIAS
                TC BR
                TZ 74150070 .DD 14NOV
                DV 17NOV/0800
                TP01 41700000000 .CP01 41-700000000
                EA01 ANA1979TESTE@GMAIL.COM
                LD01 TO TEMPORARY ADDRESS/RESIDENCIAL EXEMPLO
                TN01 LX000001
                CT01 GY22
                SI BAG OB LA3266 14NOV26 GYN STA 13H15
                FD LX92/11NOV
                RT ZRH/GRU
                /RECHARGE - /SIGNATURE FOR RECEIPT OF 1 BAG
                """);

        assertThat(bdo.passageiroNome()).isEqualTo("ANA DETESTE");
        assertThat(bdo.enderecoEntrega()).isEqualTo("R 24 N 320 APTO 1802 QD J11 LT 6/10 SETOR MARISTA");
        assertThat(bdo.uf()).isEqualTo("GO");
        assertThat(bdo.cep()).isEqualTo("74150-070");
        assertThat(bdo.telefonePassageiro()).isEqualTo("41-700000000");
        assertThat(bdo.companhiaAerea()).isEqualTo("SWISS");
        assertThat(bdo.quantidadeMalas()).isEqualTo(1);
        assertThat(bdo.aeroportoRetirada()).isEqualTo("GYN");
        assertThat(bdo.vooChegada()).isEqualTo("LA3266");
        assertThat(bdo.instrucoesEntrega()).isEqualTo("TO TEMPORARY ADDRESS/RESIDENCIAL EXEMPLO | BAG OB LA3266 14NOV26 GYN STA 13H15");
    }

    /** Mala danificada (DPR): o aeroporto vem do fim da rota com trechos. */
    @Test
    void telexDeMalaDanificada() {
        CamposExtraidosBdo bdo = leitor.ler("""
                WM BDO DPR GRUUX10001 /1405GMT/27OCT26
                DS GRUUX04 - DAMAGE REPORT- DELIVERY SERVICE
                .......... AIR EUROPA - SAO PAULO..........
                NM01 TESTE
                GN01 LUANA
                PA RUA 135 NUMERO 488 SETOR MARISTA APT3003
                YP GOIANIA
                ST GO
                CO BR
                ZP 74180020 .DD 27OCT
                CP01 5562900000002
                EA01 LUANA/U/TESTE/A/HOTMAIL.COM
                TN01 UX000001
                CT01 GY22
                FD UX57/26OCT/G31490/27OCT
                RT MAD/GRU/GYN
                BR UX57/26OCT
                """);

        assertThat(bdo.numero()).isEqualTo("GRUUX10001");
        assertThat(bdo.tipoProcesso()).isEqualTo("DPR");
        assertThat(bdo.companhiaAerea()).isEqualTo("AIR EUROPA");
        assertThat(bdo.emailPassageiro()).isEqualTo("luana_teste@hotmail.com");
        assertThat(bdo.dataEntregaPrevista()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(bdo.aeroportoRetirada()).isEqualTo("GYN");
    }

    /** Copia com "- " no inicio das linhas e a companhia so no texto ("ON BEHALF OF"); "RT LHR" sozinho e a origem. */
    @Test
    void telexComTracosEmNomeDaCompanhia() {
        CamposExtraidosBdo bdo = leitor.ler("""
                »WM DAH GIGXX10001/S
                   /DLV/
                DY01 WM BDO AHL GIGXX10001  /2335GMT/16OCT26
                - DS GIGXX01 - PACK AND GO  +55 21 900000000 -
                - THIS LUGGAGE IS BEING DELIVERED ON BEHALF OF IBERIA.........
                - NM01 SOBRENOMEEXEMPLO        .NM02 CLARA
                - PA 01R EXEMPLO 141/706 APARECIDA DE GOIANIA
                - ZP 74915380   .DD 17OCT .DW
                - CP01 55 66 900000000
                - EA01 CLARA/D/MPS/A/HOTMAIL/D/COM
                - LD01 EDF EXEMPLO
                - TN01 BA000001
                - CT01 GY22
                -  /RECHARGE -   /SIGNATURE FOR RECEIPT OF 1 BAG    .RT LHR
                END OF REPORT
                """);

        assertThat(bdo.numero()).isEqualTo("GIGXX10001");
        assertThat(bdo.companhiaAerea()).isEqualTo("IBERIA");
        assertThat(bdo.passageiroNome()).isEqualTo("SOBRENOMEEXEMPLO");
        assertThat(bdo.enderecoEntrega()).isEqualTo("01R EXEMPLO 141/706 APARECIDA DE GOIANIA");
        assertThat(bdo.cep()).isEqualTo("74915-380");
        assertThat(bdo.emailPassageiro()).isEqualTo("clara.mps@hotmail.com");
        assertThat(bdo.instrucoesEntrega()).isEqualTo("EDF EXEMPLO");
        assertThat(bdo.aeroportoRetirada()).isEqualTo("GIG");
    }

    /** Formulario impresso de uma companhia: rotulos em ingles, numero no "File Reference". */
    @Test
    void formularioImpressoDaCompanhia() {
        CamposExtraidosBdo bdo = leitor.ler("""
                UNITED
                Baggage Delivery Order GRU00001B
                PRINTED AT GRU ON 29 Oct 2026 08:35 (LOCAL TIME)
                Delivery company: PACK AND GO TRANSPORTES
                Bag Tag    Color/Desc
                0016000001  GY22
                Tracking number GRU00001B
                Final flight LA3266
                Comments DLV TOI GYN OB LA3518/29OCT ** BRL
                FILE REFERENCE : GRU00002M
                Customer Name: SOBRENOMETESTE/MARIAMRS
                Delivery Address: RUA EXEMPLO 450 APT2602 ,
                GOIANIA GO 74180070
                BR
                Contact Numbers: 62900000003
                """);

        assertThat(bdo.numero()).isEqualTo("GRU00002M");
        assertThat(bdo.passageiroNome()).isEqualTo("MARIA SOBRENOMETESTE");
        assertThat(bdo.enderecoEntrega()).isEqualTo("RUA EXEMPLO 450 APT2602");
        assertThat(bdo.cidade()).isEqualTo("GOIANIA");
        assertThat(bdo.uf()).isEqualTo("GO");
        assertThat(bdo.cep()).isEqualTo("74180-070");
        assertThat(bdo.telefonePassageiro()).isEqualTo("62900000003");
        assertThat(bdo.etiquetaBagagem()).isEqualTo("0016000001");
        assertThat(bdo.aeroportoRetirada()).isEqualTo("GYN");
        assertThat(bdo.vooChegada()).isEqualTo("LA3518");
    }

    @Test
    void textoVazioOuSemCodigosDevolveCamposVazios() {
        assertThat(leitor.ler("   ").numero()).isNull();
        CamposExtraidosBdo bdo = leitor.ler("Bom dia, segue em anexo o BDO.");
        assertThat(bdo.numero()).isNull();
        assertThat(bdo.passageiroNome()).isNull();
        assertThat(bdo.enderecoEntrega()).isNull();
    }

    /** Telex de dezembro com entrega em janeiro: a data prevista cai no ano seguinte. */
    @Test
    void entregaEmJaneiroDeTelexDeDezembro() {
        CamposExtraidosBdo bdo = leitor.ler("""
                WM BDO AHL GYNLA10002/2300GMT/30DEC26
                NM01 TESTE
                ZP 74000100 .DD 02JAN
                """);

        assertThat(bdo.dataEntregaPrevista()).isEqualTo(LocalDate.of(2027, 1, 2));
    }

    /** O WorldTracer quebra a linha longa: a de baixo, sem codigo, continua o endereco ou a instrucao. */
    @Test
    void linhaQuebradaContinuaOValorDeCima() {
        CamposExtraidosBdo bdo = leitor.ler("""
                WM BDO AHL GYNLA10003/0013GMT/16SEP26
                NM01 TESTE
                PA AV EXEMPLO 100 APT 1 JD TESTE EDF MODELO BLOCO B
                TORRE 2 PROXIMO AO MERCADO
                YP GOIANIA
                LD01 TO PERMANENT ADDRESS/LIGAR ANTES DE
                SUBIR NA PORTARIA
                TN01 LA000001
                /RECHARGE -/SIGNATURE FOR RECEIPT OF 1 BAG
                """);

        assertThat(bdo.enderecoEntrega()).isEqualTo("AV EXEMPLO 100 APT 1 JD TESTE EDF MODELO BLOCO B TORRE 2 PROXIMO AO MERCADO");
        assertThat(bdo.cidade()).isEqualTo("GOIANIA");
        assertThat(bdo.instrucoesEntrega()).isEqualTo("TO PERMANENT ADDRESS/LIGAR ANTES DE SUBIR NA PORTARIA");
        assertThat(bdo.etiquetaBagagem()).isEqualTo("LA000001");
    }

    /** E-mail malformado nao pode derrubar o INSERT nem a leitura (valor e quantidade absurdos sao contidos). */
    @Test
    void valoresAbsurdosSaoContidos() {
        CamposExtraidosBdo bdo = leitor.ler("""
                WM BDO AHL GYNLA10004/0013GMT/16SEP26
                ZP 74000100 .DD 15SEP .CS D/BRL99999999999999999999999999999999999999
                /RECHARGE -/SIGNATURE FOR RECEIPT OF 99999999999 BAG
                """);

        assertThat(bdo.valorInformado()).hasSizeLessThanOrEqualTo(30);
        assertThat(bdo.quantidadeMalas()).isNull();
    }

    /** Milhares de espacos numa linha (e-mail malicioso ou mal formatado) nao deixam a leitura lenta. */
    @Test
    void muitosEspacosNaoDeixamALeituraLenta() {
        String texto = "WM BDO AHL GYNLA10005/0013GMT/16SEP26\nNM01 TESTE" + " ".repeat(200_000) + ".GN01 ANA\u0000";

        long inicio = System.nanoTime();
        CamposExtraidosBdo bdo = leitor.ler(texto);

        assertThat((System.nanoTime() - inicio) / 1_000_000).isLessThan(2_000);
        assertThat(bdo.passageiroNome()).isEqualTo("ANA TESTE");
    }
}
