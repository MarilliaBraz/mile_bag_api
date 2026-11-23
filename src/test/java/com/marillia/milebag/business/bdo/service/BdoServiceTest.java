package com.marillia.milebag.business.bdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.marillia.milebag.business.bdo.mapper.BdoMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.DadosPassageiroBdo;
import com.marillia.milebag.business.bdo.model.BdoIncluidoPelaEmpresa;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.model.BdoRecebidoPorEmail;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class BdoServiceTest {

    @Mock
    private BdoRepository bdoRepository;
    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private com.marillia.milebag.business.configuracao.service.BaseAeroportuariaService baseAeroportuariaService;
    private BdoService bdoService;
    private final UUID administrador = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bdoService = new BdoService(bdoRepository, new BdoMapper(), new LeitorTelexBdo(), eventos, baseAeroportuariaService);
        org.mockito.Mockito.lenient().when(baseAeroportuariaService.baseDoBdo(any())).thenReturn(java.util.Optional.empty());
    }

    @Test
    void buscarPorIdDelegaParaRepository() {
        UUID id = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setId(id);
        when(bdoRepository.findById(id)).thenReturn(Optional.of(bdo));

        assertThat(bdoService.buscarPorId(id)).contains(bdo);
    }

    @Test
    void buscarPorIdRetornaVazioQuandoNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(bdoRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(bdoService.buscarPorId(id)).isEmpty();
    }

    /** HU32: o BDO do e-mail avisa os motoristas; V49: guarda o telex como chegou. */
    @Test
    void criarAutomaticoPublicaOEventoEGuardaOTexto() {
        devolverOSalvoComId();

        bdoService.criarAutomatico(new CamposExtraidosBdo("GYNLA12342", null, null, null, null, null, null), "WM BDO AHL GYNLA12342");

        ArgumentCaptor<Bdo> salvo = ArgumentCaptor.forClass(Bdo.class);
        verify(bdoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getOrigem()).isEqualTo(BdoOrigem.EMAIL_AUTOMATICO);
        assertThat(salvo.getValue().getTextoOriginal()).isEqualTo("WM BDO AHL GYNLA12342");
        ArgumentCaptor<BdoRecebidoPorEmail> evento = ArgumentCaptor.forClass(BdoRecebidoPorEmail.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().numero()).isEqualTo("GYNLA12342");
    }

    @Test
    void empresaIncluiBdoConfirmadoComEnderecoCompletoEAvisaOsMotoristas() {
        devolverOSalvoComIdNoFlush();

        var resposta = bdoService.criarPelaEmpresa(campos(" gynla 10001 ", "MARIA TESTE", "AV EXEMPLO 100", "go", "74000100", "gyn", 2),
                "WM BDO AHL GYNLA10001", administrador);

        ArgumentCaptor<Bdo> salvo = ArgumentCaptor.forClass(Bdo.class);
        verify(bdoRepository).saveAndFlush(salvo.capture());
        Bdo bdo = salvo.getValue();
        assertThat(bdo.getOrigem()).isEqualTo(BdoOrigem.EMPRESA);
        assertThat(bdo.getStatusConferencia()).isEqualTo(StatusConferencia.CONFIRMADO);
        assertThat(bdo.getIncluidoPor()).isEqualTo(administrador);
        assertThat(bdo.getNumero()).isEqualTo("GYNLA10001");
        assertThat(bdo.getEnderecoEntrega()).isEqualTo("AV EXEMPLO 100, GOIANIA - GO, 74000-100");
        assertThat(bdo.getUf()).isEqualTo("GO");
        assertThat(bdo.getCep()).isEqualTo("74000-100");
        assertThat(bdo.getAeroportoRetirada()).isEqualTo("GYN");
        assertThat(bdo.getQuantidadeMalas()).isEqualTo(2);
        assertThat(bdo.getTextoOriginal()).isEqualTo("WM BDO AHL GYNLA10001");
        assertThat(resposta.origem()).isEqualTo(BdoOrigem.EMPRESA);
        ArgumentCaptor<BdoIncluidoPelaEmpresa> evento = ArgumentCaptor.forClass(BdoIncluidoPelaEmpresa.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().numero()).isEqualTo("GYNLA10001");
    }

    @Test
    void inclusaoSemCamposMinimosERecusadaENaoGrava() {
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(campos("GYNLA00001", " ", null, null, null, null, null), null, administrador))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Campos obrigatorios ausentes: nome do passageiro, endereco de entrega.");
        verify(bdoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void inclusaoRecusaFormatosInvalidos() {
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(campos("GYNLA00001", "P", "E", "GOIAS", null, null, null), null, administrador))
                .hasMessage("A UF deve ter duas letras (ex.: GO).");
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(campos("GYNLA00001", "P", "E", null, "7400", null, null), null, administrador))
                .hasMessage("O CEP deve ter 8 numeros.");
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(campos("GYNLA00001", "P", "E", null, null, "GY1", null), null, administrador))
                .hasMessage("O aeroporto de retirada deve ser o codigo de tres letras (ex.: GYN).");
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(campos("GYNLA00001", "P", "E", null, null, null, 0), null, administrador))
                .hasMessage("A quantidade de malas deve ficar entre 1 e 20.");
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(campos("N".repeat(31), "P", "E", null, null, null, null), null, administrador))
                .hasMessage("O numero do BDO deve ter de 4 a 30 letras e numeros, sem simbolos (ex.: GYNLA24713).");
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(campos("AB/CD", "P", "E", null, null, null, null), null, administrador))
                .hasMessageStartingWith("O numero do BDO deve ter");
        CamposExtraidosBdo comAcento = new CamposExtraidosBdo("GYNLA00001", "P", "E", null, null, null, null, "AÇÃO", null, null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(comAcento, null, administrador))
                .hasMessage("O tipo do processo deve ter so letras, sem acento (ex.: AHL).");
        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(null, null, administrador))
                .hasMessage("Informe os dados do BDO.");
        verify(bdoRepository, never()).saveAndFlush(any());
    }

    @Test
    void inclusaoRecusaEmailInvalido() {
        CamposExtraidosBdo comEmail = new CamposExtraidosBdo("GYNLA00001", "P", "E", null, null, "nao-e-email", null);

        assertThatThrownBy(() -> bdoService.criarPelaEmpresa(comEmail, null, administrador))
                .hasMessage("O e-mail do passageiro nao parece valido.");
    }

    @Test
    void lerTextoDevolveOsCamposSemGravar() {
        CamposExtraidosBdo campos = bdoService.lerTexto("WM BDO AHL GYNLA10001/0013GMT/16SEP26\nNM01 TESTE\nGN01 ANA\nPA RUA EXEMPLO 1");

        assertThat(campos.numero()).isEqualTo("GYNLA10001");
        assertThat(campos.passageiroNome()).isEqualTo("ANA TESTE");
        verifyNoInteractions(bdoRepository, eventos);
    }

    @Test
    void lerTextoRecusaVazioEExcessivo() {
        assertThatThrownBy(() -> bdoService.lerTexto("  ")).hasMessage("Cole o texto do BDO.");
        assertThatThrownBy(() -> bdoService.lerTexto("A".repeat(BdoService.TAMANHO_MAXIMO_TEXTO + 1)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void buscarPorNumeroNormalizaAntesDeConsultar() {
        Bdo existente = new Bdo();
        existente.setId(UUID.randomUUID());
        existente.setNumero("GYNLA10001");
        when(bdoRepository.buscarPorNumeroNormalizado("GYNLA10001")).thenReturn(List.of(existente));

        assertThat(bdoService.buscarPorNumero(" gynla10001 ")).hasSize(1);
        assertThat(bdoService.buscarPorNumero("  ")).isEmpty();
    }

    @Test
    void enderecoCompletoNaoRepeteACidadeQueJaEstaNoLogradouro() {
        assertThat(BdoService.enderecoCompleto(
                        campos("N", "P", "RUA 1 APARECIDA DE GOIANIA", null, "74915-380", null, null, "APARECIDA DE GOIANIA")))
                .isEqualTo("RUA 1 APARECIDA DE GOIANIA, 74915-380");
        assertThat(BdoService.enderecoCompleto(new CamposExtraidosBdo("N", "P", "RUA COMPLETA, GOIANIA", null, null, null, null)))
                .isEqualTo("RUA COMPLETA, GOIANIA");
        // Rua com o nome da cidade no meio nao dispensa a cidade (a rota geocodificaria em outro lugar).
        assertThat(BdoService.enderecoCompleto(campos("N", "P", "RUA GOIANIA 5", "GO", "74000-100", null, null, "GOIANIA")))
                .isEqualTo("RUA GOIANIA 5, GOIANIA - GO, 74000-100");
    }

    /** HU16: o uso do plano conta so BDO confirmado; V47: e nao cancelado pela empresa (o filtro esta no SQL). */
    @Test
    void contarNoMesCorrenteContaSoBdoConfirmadoNaoCancelado() {
        when(bdoRepository.contarConfirmadosNaoCanceladosEntre(any(), any())).thenReturn(7L);

        assertThat(bdoService.contarNoMesCorrente()).isEqualTo(7L);
    }

    private void devolverOSalvoComId() {
        when(bdoRepository.save(any(Bdo.class))).thenAnswer(invocacao -> {
            Bdo salvo = invocacao.getArgument(0);
            salvo.setId(UUID.randomUUID());
            return salvo;
        });
    }

    private void devolverOSalvoComIdNoFlush() {
        when(bdoRepository.saveAndFlush(any(Bdo.class))).thenAnswer(invocacao -> {
            Bdo salvo = invocacao.getArgument(0);
            salvo.setId(UUID.randomUUID());
            return salvo;
        });
    }

    /** Campos digitados no formulario, com a cidade de Goiania. */
    private static CamposExtraidosBdo campos(String numero, String passageiro, String endereco, String uf, String cep,
            String aeroporto, Integer malas) {
        return campos(numero, passageiro, endereco, uf, cep, aeroporto, malas, "GOIANIA");
    }

    private static CamposExtraidosBdo campos(String numero, String passageiro, String endereco, String uf, String cep,
            String aeroporto, Integer malas, String cidade) {
        return new CamposExtraidosBdo(numero, passageiro, endereco, "LATAM", null, null, null, "AHL", cidade, uf, cep, null,
                aeroporto, null, LocalDate.of(2026, 9, 15), malas, null, null);
    }
    // --- V59: correcao dos dados do passageiro ---

    @Test
    void logradouroDeTiraOFinalQueAInclusaoAcrescentou() {
        assertThat(BdoService.logradouroDe("Rua 1, 10, Goiania - GO, 74000-000", "Goiania", "GO", "74000-000")).isEqualTo("Rua 1, 10");
        assertThat(BdoService.logradouroDe("QD 3 APARECIDA DE GOIANIA, 74900-000", "Aparecida de Goiania", "GO", "74900-000"))
                .isEqualTo("QD 3 APARECIDA DE GOIANIA");
        assertThat(BdoService.logradouroDe("Rua 1, GO", null, "GO", null)).isEqualTo("Rua 1");
        assertThat(BdoService.logradouroDe("Goiania - GO, 74000-000", "Goiania", "GO", "74000-000")).isNull();
    }

    @Test
    void logradouroDeBdoAntigoSemCidadeSeparadaVoltaInteiro() {
        assertThat(BdoService.logradouroDe("AV T-63 1200 SETOR BUENO GOIANIA", null, null, null)).isEqualTo("AV T-63 1200 SETOR BUENO GOIANIA");
    }

    @Test
    void dadosDoPassageiroMontamDeNovoOMesmoEndereco() {
        Bdo bdo = new Bdo();
        bdo.setPassageiroNome("Ana");
        bdo.setEnderecoEntrega("Rua 1, 10, Goiania - GO, 74000-000");
        bdo.setCidade("Goiania");
        bdo.setUf("GO");
        bdo.setCep("74000-000");

        DadosPassageiroBdo dados = BdoService.dadosDoPassageiro(bdo);

        assertThat(dados.enderecoEntrega()).isEqualTo("Rua 1, 10");
        assertThat(BdoService.enderecoCompleto(dados)).isEqualTo(bdo.getEnderecoEntrega());
    }

    @Test
    void validarDadosDoPassageiroNormalizaComoNaInclusao() {
        DadosPassageiroBdo validos = BdoService.validarDadosDoPassageiro(
                new DadosPassageiroBdo(" Ana ", " Rua 2 ", "Goiania", "go", "74000000", " ", "ana@exemplo.com"));

        assertThat(validos).isEqualTo(new DadosPassageiroBdo("Ana", "Rua 2", "Goiania", "GO", "74000-000", null, "ana@exemplo.com"));
    }

    @Test
    void validarDadosDoPassageiroExigeNomeEEnderecoERecusaEmailInvalido() {
        assertThatThrownBy(() -> BdoService.validarDadosDoPassageiro(new DadosPassageiroBdo(null, " ", null, null, null, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Campos obrigatorios ausentes: nome do passageiro, endereco de entrega.");
        assertThatThrownBy(() -> BdoService.validarDadosDoPassageiro(
                new DadosPassageiroBdo("Ana", "Rua 2", null, null, null, null, "ana-sem-arroba")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("e-mail");
        assertThatThrownBy(() -> BdoService.validarDadosDoPassageiro(
                new DadosPassageiroBdo("Ana", "Rua 2", null, null, "123", null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("CEP");
    }

    @Test
    void alterarDadosDoPassageiroGravaOEnderecoCompletoSemMexerNaBase() {
        Bdo bdo = new Bdo();
        UUID base = UUID.randomUUID();
        bdo.setBaseId(base);

        bdoService.alterarDadosDoPassageiro(bdo,
                new DadosPassageiroBdo("Ana", "Rua 2", "Goiania", "GO", "74000-000", "62 9", "ana@exemplo.com"), true);

        assertThat(bdo.getEnderecoEntrega()).isEqualTo("Rua 2, Goiania - GO, 74000-000");
        assertThat(bdo.getTelefonePassageiro()).isEqualTo("62 9");
        assertThat(bdo.getBaseId()).isEqualTo(base);
        verify(bdoRepository).save(bdo);
    }

    @Test
    void alterarDadosDoPassageiroSemEnderecoDeixaOEnderecoGravadoComoEsta() {
        Bdo bdo = new Bdo();
        bdo.setEnderecoEntrega("AV T-63 1200 SETOR BUENO GOIANIA");
        bdo.setCep("74000000");

        bdoService.alterarDadosDoPassageiro(bdo,
                new DadosPassageiroBdo("Ana", "AV T-63 1200 SETOR BUENO GOIANIA", null, null, "74000-000", "62 9", null), false);

        assertThat(bdo.getEnderecoEntrega()).isEqualTo("AV T-63 1200 SETOR BUENO GOIANIA");
        assertThat(bdo.getCep()).isEqualTo("74000000");
        assertThat(bdo.getTelefonePassageiro()).isEqualTo("62 9");
    }
}
