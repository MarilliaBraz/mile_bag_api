package com.marillia.milebag.business.dadosbdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.DadosPassageiroBdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.dadosbdo.model.AlteracaoDadosBdo;
import com.marillia.milebag.business.dadosbdo.model.DadosBdoAlterados;
import com.marillia.milebag.business.dadosbdo.repository.AlteracaoDadosBdoRepository;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.rota.service.RotaService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** V59: a empresa corrige nome, endereco, telefone e e-mail do passageiro ate a entrega ser concluida, sempre com motivo. */
@ExtendWith(MockitoExtension.class)
class AlteracaoDadosBdoServiceTest {

    @Mock
    private AlteracaoDadosBdoRepository repository;
    @Mock
    private BdoService bdoService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;
    @Mock
    private RotaService rotaService;
    @Mock
    private ApplicationEventPublisher eventos;

    private AlteracaoDadosBdoService servico;
    private final UUID bdoId = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID motorista = UUID.randomUUID();
    private final Bdo bdo = new Bdo();

    private static final DadosPassageiroBdo ATUAIS =
            new DadosPassageiroBdo("Ana Souza", "Rua 1, 10", "Goiania", "GO", "74000-000", "62 99999-0000", "ana@exemplo.com");

    @BeforeEach
    void setUp() {
        servico = new AlteracaoDadosBdoService(repository, bdoService, entregaService, bagagemCaronaService, rotaService, eventos);
        bdo.setId(bdoId);
        bdo.setNumero("GYNLA10001");
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        bdo.setPassageiroNome("Ana Souza");
        bdo.setEnderecoEntrega("Rua 1, 10, Goiania - GO, 74000-000");
        bdo.setCidade("Goiania");
        bdo.setUf("GO");
        bdo.setCep("74000-000");
        bdo.setTelefonePassageiro("62 99999-0000");
        bdo.setEmailPassageiro("ana@exemplo.com");
        lenient().when(bdoService.travar(bdoId)).thenReturn(Optional.of(bdo));
        lenient().when(bagagemCaronaService.entregaPrincipalIdDe(bdoId)).thenReturn(Optional.empty());
        lenient().when(repository.save(any(AlteracaoDadosBdo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void corrigeOEnderecoRegistraAntesEDepoisDescartaARotaEAvisaOMotorista() {
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, motorista);
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.of(entrega));
        when(bagagemCaronaService.listarPorEntrega(entrega.getId())).thenReturn(List.of());
        DadosPassageiroBdo novos = comEndereco("Rua 2, 20");

        AlteracaoDadosBdo alteracao = servico.alterar(bdoId, novos, "passageiro ligou mudando o endereco", admin);

        verify(bdoService).alterarDadosDoPassageiro(any(Bdo.class), any(DadosPassageiroBdo.class), org.mockito.ArgumentMatchers.eq(true));
        assertThat(alteracao.getEnderecoAnterior()).isEqualTo("Rua 1, 10, Goiania - GO, 74000-000");
        assertThat(alteracao.getEnderecoNovo()).isEqualTo("Rua 2, 20, Goiania - GO, 74000-000");
        assertThat(alteracao.getMotivo()).isEqualTo("passageiro ligou mudando o endereco");
        assertThat(alteracao.getAlteradoPor()).isEqualTo(admin);
        verify(rotaService).aoCorrigirEnderecoDeEntrega(entrega.getId(), "Rua 2, 20, Goiania - GO, 74000-000", false);
        ArgumentCaptor<DadosBdoAlterados> evento = ArgumentCaptor.forClass(DadosBdoAlterados.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().motoristaId()).isEqualTo(motorista);
        assertThat(evento.getValue().enderecoMudou()).isTrue();
    }

    @Test
    void comAViagemEmAndamentoAtualizaODestinoEmVezDeDescartar() {
        Entrega entrega = entrega(EntregaStatus.EM_ANDAMENTO, motorista);
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.of(entrega));
        when(bagagemCaronaService.listarPorEntrega(entrega.getId())).thenReturn(List.of());

        servico.alterar(bdoId, comEndereco("Rua 2, 20"), "endereco errado", admin);

        verify(rotaService).aoCorrigirEnderecoDeEntrega(entrega.getId(), "Rua 2, 20, Goiania - GO, 74000-000", true);
    }

    @Test
    void soOTelefoneNaoMexeNaRotaEAvisaSemFalarDeEndereco() {
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, motorista);
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.of(entrega));

        servico.alterar(bdoId, new DadosPassageiroBdo("Ana Souza", "Rua 1, 10", "Goiania", "GO", "74000-000", "62 98888-1111",
                "ana@exemplo.com"), "telefone novo", admin);

        verify(rotaService, never()).aoCorrigirEnderecoDeEntrega(any(), anyString(), anyBoolean());
        ArgumentCaptor<DadosBdoAlterados> evento = ArgumentCaptor.forClass(DadosBdoAlterados.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().enderecoMudou()).isFalse();
    }

    @Test
    void bdoSemEntregaNaoMexeNaRotaNemAvisaNinguem() {
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.empty());

        servico.alterar(bdoId, comEndereco("Rua 2, 20"), "endereco errado", admin);

        verify(repository).save(any(AlteracaoDadosBdo.class));
        verify(rotaService, never()).aoCorrigirEnderecoDeEntrega(any(), anyString(), anyBoolean());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void semMotivoRecusaSemGravar() {
        assertThatThrownBy(() -> servico.alterar(bdoId, comEndereco("Rua 2, 20"), "  ", admin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("motivo");
        verify(bdoService, never()).alterarDadosDoPassageiro(any(), any(), anyBoolean());
    }

    @Test
    void entregaConcluidaNaoPodeMaisSerAlterada() {
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.of(entrega(EntregaStatus.ENCERRADA_COM_SUCESSO, motorista)));

        assertThatThrownBy(() -> servico.alterar(bdoId, comEndereco("Rua 2, 20"), "motivo", admin))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("concluida");
        verify(repository, never()).save(any());
    }

    @Test
    void bdoCanceladoNaoPodeSerAlterado() {
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.of(entrega(EntregaStatus.CANCELADA, null)));

        assertThatThrownBy(() -> servico.alterar(bdoId, comEndereco("Rua 2, 20"), "motivo", admin))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void caronaJaEntregueComAPrincipalNaoPodeSerAlterada() {
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.empty());
        comoCaronaDe(entrega(EntregaStatus.ENCERRADA_COM_SUCESSO, motorista));

        assertThatThrownBy(() -> servico.alterar(bdoId, comTelefone("62 98888-1111"), "motivo", admin))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("concluida");
    }

    @Test
    void caronaNaoMudaDeEnderecoMasPodeCorrigirOTelefoneEAvisaOMotoristaDaPrincipal() {
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.empty());
        comoCaronaDe(entrega(EntregaStatus.EM_ANDAMENTO, motorista));

        assertThatThrownBy(() -> servico.alterar(bdoId, comEndereco("Rua 2, 20"), "motivo", admin))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("carona");

        servico.alterar(bdoId, comTelefone("62 98888-1111"), "telefone novo", admin);
        ArgumentCaptor<DadosBdoAlterados> evento = ArgumentCaptor.forClass(DadosBdoAlterados.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().motoristaId()).isEqualTo(motorista);
    }

    @Test
    void entregaQueLevaCaronaNaoMudaDeEndereco() {
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, motorista);
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.of(entrega));
        when(bagagemCaronaService.listarPorEntrega(entrega.getId()))
                .thenReturn(List.of(new BagagemCarona(UUID.randomUUID(), entrega.getId(), java.math.BigDecimal.ONE)));

        assertThatThrownBy(() -> servico.alterar(bdoId, comEndereco("Rua 2, 20"), "motivo", admin))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("carona");
    }

    @Test
    void mudarSoOCepTambemContaComoEndereco() {
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.empty());

        AlteracaoDadosBdo alteracao = servico.alterar(bdoId,
                new DadosPassageiroBdo("Ana Souza", "Rua 1, 10", "Goiania", "GO", "74100000", "62 99999-0000", "ana@exemplo.com"),
                "cep errado", admin);

        assertThat(alteracao.getEnderecoNovo()).isEqualTo("Rua 1, 10, Goiania - GO, 74100-000");
    }

    @Test
    void semNadaDiferenteRecusa() {
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.alterar(bdoId, ATUAIS, "motivo", admin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Nenhum dado foi alterado.");
    }

    @Test
    void bdoQueNaoExisteDa404() {
        when(bdoService.travar(bdoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.alterar(bdoId, comEndereco("Rua 2, 20"), "motivo", admin))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void dadosAtuaisVemComOLogradouroSeparado() {
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));

        assertThat(servico.dados(bdoId)).isEqualTo(ATUAIS);
    }

    private void comoCaronaDe(Entrega principal) {
        when(bagagemCaronaService.entregaPrincipalIdDe(bdoId)).thenReturn(Optional.of(principal.getId()));
        when(entregaService.travarParaCorrigir(principal.getId())).thenReturn(principal);
    }

    @Test
    void bdoAntigoComEnderecoNumCampoSoNaoMudaDeEnderecoAoCorrigirOTelefone() {
        bdo.setEnderecoEntrega("AV T-63 1200 SETOR BUENO GOIANIA");
        bdo.setCidade(null);
        bdo.setUf(null);
        bdo.setCep("74000000");
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, motorista);
        when(entregaService.travarPorBdo(bdoId)).thenReturn(Optional.of(entrega));

        AlteracaoDadosBdo alteracao = servico.alterar(bdoId, new DadosPassageiroBdo("Ana Souza", "AV T-63 1200 SETOR BUENO GOIANIA",
                null, null, "74000-000", "62 98888-1111", "ana@exemplo.com"), "telefone novo", admin);

        verify(bdoService).alterarDadosDoPassageiro(any(Bdo.class), any(DadosPassageiroBdo.class), org.mockito.ArgumentMatchers.eq(false));
        assertThat(alteracao.getEnderecoNovo()).isEqualTo("AV T-63 1200 SETOR BUENO GOIANIA");
        verify(rotaService, never()).aoCorrigirEnderecoDeEntrega(any(), anyString(), anyBoolean());
    }

    private DadosPassageiroBdo comEndereco(String logradouro) {
        return new DadosPassageiroBdo(ATUAIS.passageiroNome(), logradouro, ATUAIS.cidade(), ATUAIS.uf(), ATUAIS.cep(),
                ATUAIS.telefonePassageiro(), ATUAIS.emailPassageiro());
    }

    private DadosPassageiroBdo comTelefone(String telefone) {
        return new DadosPassageiroBdo(ATUAIS.passageiroNome(), ATUAIS.enderecoEntrega(), ATUAIS.cidade(), ATUAIS.uf(), ATUAIS.cep(),
                telefone, ATUAIS.emailPassageiro());
    }

    private Entrega entrega(EntregaStatus status, UUID motoristaResponsavel) {
        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setBdoId(bdoId);
        entrega.setStatus(status);
        entrega.setMotoristaResponsavel(motoristaResponsavel);
        return entrega;
    }
}
