package com.marillia.milebag.business.cancelamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.cancelamento.model.BdoCancelado;
import com.marillia.milebag.business.cancelamento.model.CancelamentoBdo;
import com.marillia.milebag.business.cancelamento.model.CancelamentoBdoResponse;
import com.marillia.milebag.business.cancelamento.model.CancelarBdoRequest;
import com.marillia.milebag.business.cancelamento.repository.CancelamentoBdoRepository;
import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.entrega.service.EntregaService.EntregaCancelada;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class CancelamentoBdoServiceTest {

    @Mock
    private CancelamentoBdoRepository cancelamentoBdoRepository;
    @Mock
    private BdoService bdoService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;
    @Mock
    private ViagemService viagemService;
    @Mock
    private MotivoOcorrenciaService motivoOcorrenciaService;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;
    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private CancelamentoBdoService cancelamentoBdoService;

    private final UUID bdoId = UUID.randomUUID();
    private final UUID entregaId = UUID.randomUUID();
    private final UUID empresa = UUID.randomUUID();
    private final UUID motivoId = UUID.randomUUID();
    private MotivoOcorrencia motivo;

    @BeforeEach
    void setUp() {
        motivo = new MotivoOcorrencia("Enviado por engano", false, FinalidadeMotivo.CANCELAMENTO);
        org.springframework.test.util.ReflectionTestUtils.setField(motivo, "id", motivoId);
    }

    @Test
    void motivoEObrigatorio() {
        bdoConfirmado();

        assertThatThrownBy(() -> cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(null, "engano")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> cancelamentoBdoService.cancelar(bdoId, empresa, null)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(entregaService, cancelamentoBdoRepository);
    }

    /** Foto ainda em conferencia nao e BDO de verdade (e tem o proprio descarte, HU01). */
    @Test
    void bdoNaoConfirmadoE404() {
        Bdo pendente = new Bdo();
        pendente.setStatusConferencia(StatusConferencia.PENDENTE);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(entregaService);
    }

    @Test
    void oMotivoPrecisaSerDaListaDeCancelamento() {
        bdoConfirmado();
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId, FinalidadeMotivo.CANCELAMENTO))
                .thenThrow(new RecursoNaoEncontradoException("Motivo nao encontrado."));

        assertThatThrownBy(() -> cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(entregaService);
    }

    @Test
    void complementoMuitoLongoERecusado() {
        bdoConfirmado();
        motivoValido();

        assertThatThrownBy(() -> cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, "x".repeat(501))))
                .isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(entregaService);
    }

    /** Ninguem tinha assumido: so o registro - sem viagem a encerrar e sem motorista a avisar. */
    @Test
    void bdoQueNinguemAssumiuECanceladoSemAvisoNemViagem() {
        bdoConfirmado();
        motivoValido();
        when(entregaService.cancelar(bdoId)).thenReturn(new EntregaCancelada(entregaId, null, null));
        salvarDevolvendoComId();

        CancelamentoBdoResponse resposta =
                cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, "  enviado duas vezes  "));

        ArgumentCaptor<CancelamentoBdo> salvo = ArgumentCaptor.forClass(CancelamentoBdo.class);
        verify(cancelamentoBdoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getComplemento()).isEqualTo("enviado duas vezes");
        assertThat(salvo.getValue().getCanceladoPor()).isEqualTo(empresa);
        assertThat(salvo.getValue().getMotoristaAnteriorId()).isNull();
        assertThat(salvo.getValue().isEmRota()).isFalse();
        verify(bagagemCaronaService).soltarDoCancelamento(bdoId, entregaId);
        verify(viagemService, never()).encerrarPorCancelamento(any(), any());
        verify(eventos, never()).publishEvent(any());
        assertThat(resposta.motivo()).isEqualTo("Enviado por engano");
    }

    /** Em rota: a viagem e encerrada pelo cancelamento (sem pagamento) e o motorista e avisado com o motivo. */
    @Test
    void entregaEmRotaEncerraAViagemEAvisaOMotorista() {
        UUID motorista = UUID.randomUUID();
        bdoConfirmado();
        motivoValido();
        when(entregaService.cancelar(bdoId)).thenReturn(new EntregaCancelada(entregaId, motorista, EntregaStatus.EM_ANDAMENTO));
        salvarDevolvendoComId();

        cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null));

        ArgumentCaptor<CancelamentoBdo> salvo = ArgumentCaptor.forClass(CancelamentoBdo.class);
        verify(cancelamentoBdoRepository).save(salvo.capture());
        assertThat(salvo.getValue().isEmRota()).isTrue();
        assertThat(salvo.getValue().getMotoristaAnteriorId()).isEqualTo(motorista);
        verify(viagemService).encerrarPorCancelamento(entregaId, salvo.getValue().getId());
        ArgumentCaptor<BdoCancelado> aviso = ArgumentCaptor.forClass(BdoCancelado.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getValue().motoristaId()).isEqualTo(motorista);
        assertThat(aviso.getValue().bdoNumero()).isEqualTo("GYN-123");
        assertThat(aviso.getValue().motivo()).isEqualTo("Enviado por engano");
    }

    /** Atribuida mas sem sair: avisa o motorista, mas nao ha viagem a encerrar. */
    @Test
    void entregaAtribuidaAvisaOMotoristaSemMexerEmViagem() {
        bdoConfirmado();
        motivoValido();
        when(entregaService.cancelar(bdoId)).thenReturn(new EntregaCancelada(entregaId, UUID.randomUUID(), EntregaStatus.ATRIBUIDA));
        salvarDevolvendoComId();

        cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null));

        verify(viagemService, never()).encerrarPorCancelamento(any(), any());
        verify(eventos).publishEvent(any(BdoCancelado.class));
    }

    /** A entrega e cancelada primeiro: concluida ou ja cancelada (409), nada mais e gravado nem avisado. */
    @Test
    void entregaQueNaoPodeSerCanceladaNaoGravaNada() {
        bdoConfirmado();
        motivoValido();
        when(entregaService.cancelar(bdoId)).thenThrow(new ConflitoException("Esta entrega ja foi concluida e nao pode ser cancelada."));

        assertThatThrownBy(() -> cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null)))
                .isInstanceOf(ConflitoException.class);
        verifyNoInteractions(cancelamentoBdoRepository, viagemService, eventos);
        verify(bagagemCaronaService, never()).soltarDoCancelamento(any(), any());
    }

    @Test
    void ordemDosEfeitos() {
        bdoConfirmado();
        motivoValido();
        when(entregaService.cancelar(bdoId)).thenReturn(new EntregaCancelada(entregaId, UUID.randomUUID(), EntregaStatus.EM_ANDAMENTO));
        salvarDevolvendoComId();

        cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null));

        InOrder ordem = inOrder(entregaService, bagagemCaronaService, cancelamentoBdoRepository, viagemService, eventos);
        ordem.verify(entregaService).cancelar(bdoId);
        ordem.verify(bagagemCaronaService).soltarDoCancelamento(bdoId, entregaId);
        ordem.verify(cancelamentoBdoRepository).save(any());
        ordem.verify(viagemService).encerrarPorCancelamento(eq(entregaId), any());
        ordem.verify(eventos).publishEvent(any(BdoCancelado.class));
    }

    private void bdoConfirmado() {
        Bdo bdo = new Bdo();
        bdo.setId(bdoId);
        bdo.setNumero("GYN-123");
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));
    }

    private void motivoValido() {
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId, FinalidadeMotivo.CANCELAMENTO)).thenReturn(motivo);
    }

    private void salvarDevolvendoComId() {
        when(cancelamentoBdoRepository.save(any(CancelamentoBdo.class))).thenAnswer(invocacao -> {
            CancelamentoBdo cancelamento = invocacao.getArgument(0);
            cancelamento.setId(UUID.randomUUID());
            return cancelamento;
        });
        when(motivoOcorrenciaService.descricao(motivoId)).thenReturn(Optional.of("Enviado por engano"));
    }

    /** Carona ja entregue com a principal: nao ha o que cancelar (e o km complementar ja entrou no pagamento). */
    @Test
    void caronaJaEntregueComAPrincipalNaoPodeSerCancelada() {
        bdoConfirmado();
        motivoValido();
        when(bagagemCaronaService.entregaPrincipalDe(bdoId)).thenReturn(Optional.of(principal(EntregaStatus.ENCERRADA_COM_SUCESSO, UUID.randomUUID())));

        assertThatThrownBy(() -> cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null)))
                .isInstanceOf(ConflitoException.class);
        verifyNoInteractions(entregaService, cancelamentoBdoRepository, eventos);
    }

    /** Carona de entrega em aberto: quem leva a mala e o motorista da principal - ele e avisado. */
    @Test
    void caronaDeEntregaEmAbertoAvisaOMotoristaDaPrincipal() {
        UUID motoristaDaPrincipal = UUID.randomUUID();
        bdoConfirmado();
        motivoValido();
        when(bagagemCaronaService.entregaPrincipalDe(bdoId)).thenReturn(Optional.of(principal(EntregaStatus.EM_ANDAMENTO, motoristaDaPrincipal)));
        when(entregaService.cancelar(bdoId)).thenReturn(new EntregaCancelada(entregaId, null, null));
        salvarDevolvendoComId();

        cancelamentoBdoService.cancelar(bdoId, empresa, new CancelarBdoRequest(motivoId, null));

        ArgumentCaptor<BdoCancelado> aviso = ArgumentCaptor.forClass(BdoCancelado.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getValue().motoristaId()).isEqualTo(motoristaDaPrincipal);
        verify(bagagemCaronaService).soltarDoCancelamento(bdoId, entregaId);
        verify(viagemService, never()).encerrarPorCancelamento(any(), any());
    }

    private static com.marillia.milebag.business.entrega.model.Entrega principal(EntregaStatus status, UUID motorista) {
        com.marillia.milebag.business.entrega.model.Entrega entrega = new com.marillia.milebag.business.entrega.model.Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setStatus(status);
        entrega.setMotoristaResponsavel(motorista);
        return entrega;
    }
}
