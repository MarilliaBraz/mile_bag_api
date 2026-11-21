package com.marillia.milebag.business.segundavia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.segundavia.model.EnvioSegundaViaBdo;
import com.marillia.milebag.business.segundavia.repository.EnvioSegundaViaBdoRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.service.LimitadorSolicitacoes;
import com.marillia.milebag.business.tenant.service.TenantService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.config.SmtpProperties;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.EnvioIndisponivelException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.mail.AnexoEmail;
import com.marillia.milebag.core.mail.EmailSender;

@ExtendWith(MockitoExtension.class)
class SegundaViaBdoServiceTest {

    @Mock
    private BdoService bdoService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;
    @Mock
    private EnvioSegundaViaBdoRepository envioRepository;
    @Mock
    private EmailSender emailSender;
    @Mock
    private LimitadorSolicitacoes limitador;
    @Mock
    private TenantService tenantService;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;

    private final UUID tenant = UUID.randomUUID();
    private final UUID motorista = UUID.randomUUID();
    private final UUID entregaId = UUID.randomUUID();
    private final Bdo principal = bdo("GYNLA10001", 2, "WM BDO AHL GYNLA10001/0013GMT/16SEP26\nNM01 FICTICIA\nPA AV EXEMPLO 100");
    private final Bdo carona = bdo("GYNLA10002", null, null);

    @BeforeEach
    void setUp() {
        TenantContext.set(tenant);
        lenient().when(tenantService.buscar(tenant)).thenReturn(new Tenant("empresa-teste", "Transportes Exemplo"));
        lenient().when(bdoService.buscarPorId(principal.getId())).thenReturn(Optional.of(principal));
        lenient().when(bdoService.buscarPorId(carona.getId())).thenReturn(Optional.of(carona));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    private SegundaViaBdoService servico(boolean smtpLigado) {
        return new SegundaViaBdoService(bdoService, entregaService, bagagemCaronaService, envioRepository, emailSender,
                new SmtpProperties(smtpLigado, "smtp.exemplo", 587, "u", "s", "nao-responda@exemplo.com"), limitador, tenantService,
                usuarioConsultaService);
    }

    @Test
    void motoristaResponsavelRecebeUmaPaginaPorBdoComAsCaronas() throws Exception {
        entregaComOMotorista(EntregaStatus.EM_ANDAMENTO);
        when(bagagemCaronaService.listarPorEntrega(entregaId)).thenReturn(List.of(new BagagemCarona(carona.getId(), entregaId, BigDecimal.ZERO)));

        SegundaViaBdoService.ArquivoPdf arquivo = servico(true).pdfDoMotorista(entregaId, motorista);

        assertThat(arquivo.nomeArquivo()).isEqualTo("BDO-GYNLA10001-segunda-via.pdf");
        try (PDDocument pdf = PDDocument.load(arquivo.conteudo())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    void outroMotoristaNaoEmite() {
        Entrega entrega = entregaComOMotorista(EntregaStatus.ATRIBUIDA);

        assertThatThrownBy(() -> servico(true).pdfDoMotorista(entrega.getId(), UUID.randomUUID()))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void entregaEncerradaNaoEmiteParaOMotorista() {
        entregaComOMotorista(EntregaStatus.ENCERRADA_COM_SUCESSO);

        assertThatThrownBy(() -> servico(true).pdfDoMotorista(entregaId, motorista))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("A segunda via so pode ser emitida enquanto a entrega estiver com voce.");
    }

    @Test
    void entregaCanceladaMostraAMensagemDaEmpresa() {
        entregaComOMotorista(EntregaStatus.CANCELADA);

        assertThatThrownBy(() -> servico(true).pdfDoMotorista(entregaId, motorista))
                .hasMessage(EntregaService.MENSAGEM_CANCELADA);
    }

    @Test
    void empresaEmiteQualquerBdo() {
        assertThat(servico(true).pdfDaEmpresa(principal.getId()).conteudo()).startsWith("%PDF".getBytes());
        verifyNoInteractions(entregaService);
    }

    @Test
    void envioDoMotoristaAnexaOPdfERegistraCadaBdo() throws Exception {
        entregaComOMotorista(EntregaStatus.ATRIBUIDA);
        when(bagagemCaronaService.listarPorEntrega(entregaId)).thenReturn(List.of(new BagagemCarona(carona.getId(), entregaId, BigDecimal.ZERO)));
        when(limitador.permitir("segunda-via:" + motorista)).thenReturn(true);
        when(usuarioConsultaService.nomeParaExibicao(motorista)).thenReturn(Optional.of("Motorista Teste"));

        servico(true).enviarDoMotorista(entregaId, motorista, " Grafica@Exemplo.com ");

        ArgumentCaptor<AnexoEmail> anexo = ArgumentCaptor.forClass(AnexoEmail.class);
        verify(emailSender).enviarComAnexo(eq("grafica@exemplo.com"), eq("BDO GYNLA10001 - segunda via para impressão"),
                org.mockito.ArgumentMatchers.contains("Enviado por Motorista Teste"), anexo.capture());
        assertThat(anexo.getValue().tipoConteudo()).isEqualTo("application/pdf");
        ArgumentCaptor<EnvioSegundaViaBdo> registro = ArgumentCaptor.forClass(EnvioSegundaViaBdo.class);
        verify(envioRepository, times(2)).save(registro.capture());
        assertThat(registro.getAllValues()).extracting(EnvioSegundaViaBdo::getBdoId).containsExactly(principal.getId(), carona.getId());
        assertThat(registro.getValue().getDestinatario()).isEqualTo("grafica@exemplo.com");
    }

    @Test
    void comOEnvioDesligadoAvisaENaoRegistra() throws Exception {
        assertThatThrownBy(() -> servico(false).enviarDaEmpresa(principal.getId(), UUID.randomUUID(), "grafica@exemplo.com"))
                .isInstanceOf(EnvioIndisponivelException.class)
                .hasMessage("O envio de e-mail esta desativado neste ambiente. Use o botao Imprimir.");
        verify(emailSender, never()).enviarComAnexo(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(envioRepository, limitador);
    }

    @Test
    void emailInvalidoERecusado() {
        assertThatThrownBy(() -> servico(true).enviarDaEmpresa(principal.getId(), UUID.randomUUID(), "nao-e-email"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O e-mail informado nao parece valido.");
        assertThatThrownBy(() -> servico(true).enviarDaEmpresa(principal.getId(), UUID.randomUUID(), " "))
                .hasMessage("Informe o e-mail para onde enviar o BDO.");
        assertThatThrownBy(() -> servico(true).enviarDaEmpresa(principal.getId(), UUID.randomUUID(), "a@b.com,c@d.com"))
                .hasMessage("O e-mail informado nao parece valido.");
    }

    @Test
    void acimaDoLimiteNaoEnvia() throws Exception {
        UUID admin = UUID.randomUUID();
        when(limitador.permitir("segunda-via:" + admin + ":" + principal.getId())).thenReturn(false);

        assertThatThrownBy(() -> servico(true).enviarDaEmpresa(principal.getId(), admin, "grafica@exemplo.com"))
                .isInstanceOf(RegraNegocioException.class);
        verify(emailSender, never()).enviarComAnexo(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(envioRepository);
    }

    @Test
    void falhaNoSmtpNaoRegistraOEnvio() throws Exception {
        UUID admin = UUID.randomUUID();
        when(limitador.permitir(anyString())).thenReturn(true);
        doThrow(new jakarta.mail.MessagingException("fora")).when(emailSender).enviarComAnexo(anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> servico(true).enviarDaEmpresa(principal.getId(), admin, "grafica@exemplo.com"))
                .isInstanceOf(EnvioIndisponivelException.class);
        verifyNoInteractions(envioRepository);
    }

    @Test
    void umCodigoDeBarrasPorMalaComoNoPapelOriginal() {
        assertThat(SegundaViaBdoPdf.codigosDeBarras(principal)).containsExactly("AHLGYNLA10001-01", "AHLGYNLA10001-02");
        assertThat(SegundaViaBdoPdf.codigosDeBarras(bdo("AB/CD", null, null))).isEmpty();
        Bdo comAcento = bdo("GYNLA10004", 1, null);
        comAcento.setTipoProcesso("AÇÃO");
        assertThat(SegundaViaBdoPdf.codigosDeBarras(comAcento)).containsExactly("AOGYNLA10004-01");
        assertThat(SegundaViaBdoPdf.gerar(java.util.List.of(comAcento), "Empresa", java.time.Instant.now())).isNotEmpty();
    }

    @Test
    void semTelexOsCamposSaemNoMesmoFormato() {
        Bdo digitado = bdo("GYNLA10003", 1, null);
        digitado.setPassageiroNome("MARIA FICTICIA");
        digitado.setEnderecoEntrega("AV EXEMPLO 100, GOIANIA - GO, 74000-100");

        assertThat(SegundaViaBdoPdf.linhasDoTelex(digitado))
                .containsSequence("NOME: MARIA FICTICIA", "ENDERECO: AV EXEMPLO 100, GOIANIA - GO, 74000-100");
    }

    private Entrega entregaComOMotorista(EntregaStatus status) {
        Entrega entrega = new Entrega();
        entrega.setId(entregaId);
        entrega.setBdoId(principal.getId());
        entrega.setMotoristaResponsavel(motorista);
        entrega.setStatus(status);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        return entrega;
    }

    private static Bdo bdo(String numero, Integer malas, String texto) {
        Bdo bdo = new Bdo();
        bdo.setId(UUID.randomUUID());
        bdo.setNumero(numero);
        bdo.setTipoProcesso("AHL");
        bdo.setQuantidadeMalas(malas);
        bdo.setTextoOriginal(texto);
        return bdo;
    }
}
