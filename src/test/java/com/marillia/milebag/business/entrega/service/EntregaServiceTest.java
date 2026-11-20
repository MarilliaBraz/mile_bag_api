package com.marillia.milebag.business.entrega.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaRepository;
import com.marillia.milebag.business.entrega.mapper.EntregaMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Cobre os metodos adicionados no refactor de acoplamento: EntregaService
 * passa a ser o unico ponto de escrita em Entrega para outros dominios
 * (viagem/comprovante/ocorrencia chamavam entregaRepository.save direto
 * antes) e passa a consultar Bdo via BdoService em vez de BdoRepository.
 */
@ExtendWith(MockitoExtension.class)
class EntregaServiceTest {

    @Mock
    private EntregaRepository entregaRepository;
    @Mock
    private BdoService bdoService;
    @Mock
    private EntregaMapper entregaMapper;
    @Mock
    private BagagemCaronaRepository bagagemCaronaRepository;

    @InjectMocks
    private EntregaService entregaService;

    private Entrega novaEntrega(UUID id, EntregaStatus status) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(status);
        return entrega;
    }

    @Test
    void marcarEmAndamentoAtualizaStatusEPersiste() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.ATRIBUIDA);
        when(entregaRepository.findForUpdateById(entregaId)).thenReturn(Optional.of(entrega));

        entregaService.marcarEmAndamento(entregaId);

        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.EM_ANDAMENTO);
        verify(entregaRepository).save(entrega);
    }

    @Test
    void marcarEncerradaComSucessoAtualizaStatusEPersiste() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.EM_ANDAMENTO);
        when(entregaRepository.findForUpdateById(entregaId)).thenReturn(Optional.of(entrega));

        entregaService.marcarEncerradaComSucesso(entregaId);

        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.ENCERRADA_COM_SUCESSO);
        verify(entregaRepository).save(entrega);
    }

    @Test
    void reabrirParaNovaTentativaLimpaMotoristaEReabre() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.EM_ANDAMENTO);
        entrega.setMotoristaResponsavel(UUID.randomUUID());
        when(entregaRepository.findForUpdateById(entregaId)).thenReturn(Optional.of(entrega));

        entregaService.reabrirParaNovaTentativa(entregaId, null);

        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.REABERTA);
        assertThat(entrega.getMotoristaResponsavel()).isNull();
        verify(entregaRepository).save(entrega);
    }

    @Test
    void devolverAposDispensaUsaAAtualizacaoCondicionalComOStatusCerto() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        when(entregaRepository.devolverSeAindaDoMotorista(entregaId, motoristaId, "AGUARDANDO_ATRIBUICAO")).thenReturn(1);
        when(entregaRepository.devolverSeAindaDoMotorista(entregaId, motoristaId, "REABERTA")).thenReturn(1);

        entregaService.devolverAposDispensa(entregaId, motoristaId, false);
        entregaService.devolverAposDispensa(entregaId, motoristaId, true);

        verify(entregaRepository).devolverSeAindaDoMotorista(entregaId, motoristaId, "AGUARDANDO_ATRIBUICAO");
        verify(entregaRepository).devolverSeAindaDoMotorista(entregaId, motoristaId, "REABERTA");
    }

    /** Concluida (ou ja dispensada) no mesmo instante: nada muda e a dispensa inteira e desfeita. */
    @Test
    void devolverAposDispensaFalhaSeAEntregaJaMudou() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        when(entregaRepository.devolverSeAindaDoMotorista(entregaId, motoristaId, "REABERTA")).thenReturn(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> entregaService.devolverAposDispensa(entregaId, motoristaId, true))
                .isInstanceOf(com.marillia.milebag.core.exception.ConflitoException.class);
    }

    @Test
    void reabrirComReagendamentoGuardaJanelaENovaOcorrenciaSemJanelaADescarta() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.EM_ANDAMENTO);
        when(entregaRepository.findForUpdateById(entregaId)).thenReturn(Optional.of(entrega));
        JanelaReagendamento janela =
                new JanelaReagendamento(Instant.parse("2026-10-23T17:00:00Z"), Instant.parse("2026-10-23T19:00:00Z"));

        entregaService.reabrirParaNovaTentativa(entregaId, janela);
        assertThat(entrega.janelaReagendamento()).isEqualTo(janela);

        entregaService.reabrirParaNovaTentativa(entregaId, null);
        assertThat(entrega.janelaReagendamento()).isNull();
    }

    @Test
    void marcarEmAndamentoLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaRepository.findForUpdateById(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entregaService.marcarEmAndamento(entregaId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(entregaRepository, never()).save(any());
    }

    @Test
    void buscarPorIdDelegaParaRepository() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.AGUARDANDO_ATRIBUICAO);
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.of(entrega));

        assertThat(entregaService.buscarPorId(entregaId)).contains(entrega);
    }

    @Test
    void autoatribuirConsultaBdoViaBdoServiceEExigeConfirmado() {
        UUID bdoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setStatusConferencia(StatusConferencia.PENDENTE);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));

        assertThatThrownBy(() -> entregaService.autoatribuir(bdoId, motoristaId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(entregaRepository, never()).autoatribuirSeDisponivel(any(), any(), any(), any());
    }

    @Test
    void autoatribuirLancaConflitoQuandoNenhumaLinhaAtualizada() {
        UUID bdoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));
        when(entregaRepository.autoatribuirSeDisponivel(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> entregaService.autoatribuir(bdoId, motoristaId))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void autoatribuirRecusaBdoJaVinculadoComoBagagemCarona() {
        UUID bdoId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));
        when(bagagemCaronaRepository.findByBdoId(bdoId)).thenReturn(Optional.of(new BagagemCarona(bdoId, UUID.randomUUID(), BigDecimal.ONE)));

        assertThatThrownBy(() -> entregaService.autoatribuir(bdoId, UUID.randomUUID())).isInstanceOf(ConflitoException.class);
        verify(entregaRepository, never()).autoatribuirSeDisponivel(any(), any(), any(), any());
    }

    /** V47: a entrega cancelada pela empresa nao volta a andar (viagem, comprovante, ocorrencia) - nem por corrida. */
    @Test
    void mudancasDeSituacaoRecusamAEntregaCanceladaComAMensagemDaEmpresa() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.CANCELADA);
        when(entregaRepository.findForUpdateById(entregaId)).thenReturn(Optional.of(entrega));

        assertThatThrownBy(() -> entregaService.marcarEmAndamento(entregaId))
                .isInstanceOf(ConflitoException.class).hasMessage(EntregaService.MENSAGEM_CANCELADA);
        assertThatThrownBy(() -> entregaService.marcarEncerradaComSucesso(entregaId)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> entregaService.reabrirParaNovaTentativa(entregaId, null)).isInstanceOf(ConflitoException.class);
        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.CANCELADA);
        verify(entregaRepository, never()).save(any());
    }

    @Test
    void cancelarBdoSemEntregaCriaUmaJaCancelada() {
        UUID bdoId = UUID.randomUUID();
        when(entregaRepository.findForUpdateByBdoId(bdoId)).thenReturn(Optional.empty());
        when(entregaRepository.criarCancelada(any(), any(), org.mockito.ArgumentMatchers.eq(bdoId))).thenReturn(1);

        EntregaService.EntregaCancelada cancelada = entregaService.cancelar(bdoId);

        assertThat(cancelada.entregaId()).isNotNull();
        assertThat(cancelada.motoristaAnterior()).isNull();
        assertThat(cancelada.estavaEmRota()).isFalse();
    }

    /** Um motorista assumiu o BDO no mesmo instante: o cancelamento nao pisa na entrega dele. */
    @Test
    void cancelarBdoQueUmMotoristaAcabouDeAssumirE409() {
        UUID bdoId = UUID.randomUUID();
        when(entregaRepository.findForUpdateByBdoId(bdoId)).thenReturn(Optional.empty());
        when(entregaRepository.criarCancelada(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> entregaService.cancelar(bdoId)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void cancelarEntregaEmRotaTiraOMotoristaEDevolveQuemEstavaComEla() {
        UUID bdoId = UUID.randomUUID();
        UUID motorista = UUID.randomUUID();
        Entrega entrega = novaEntrega(UUID.randomUUID(), EntregaStatus.EM_ANDAMENTO);
        entrega.setMotoristaResponsavel(motorista);
        when(entregaRepository.findForUpdateByBdoId(bdoId)).thenReturn(Optional.of(entrega));

        EntregaService.EntregaCancelada cancelada = entregaService.cancelar(bdoId);

        assertThat(cancelada.entregaId()).isEqualTo(entrega.getId());
        assertThat(cancelada.motoristaAnterior()).isEqualTo(motorista);
        assertThat(cancelada.estavaEmRota()).isTrue();
        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.CANCELADA);
        assertThat(entrega.getMotoristaResponsavel()).isNull();
        verify(entregaRepository).save(entrega);
    }

    @Test
    void cancelarEntregaConcluidaOuJaCanceladaE409() {
        UUID concluida = UUID.randomUUID();
        UUID jaCancelada = UUID.randomUUID();
        when(entregaRepository.findForUpdateByBdoId(concluida))
                .thenReturn(Optional.of(novaEntrega(UUID.randomUUID(), EntregaStatus.ENCERRADA_COM_SUCESSO)));
        when(entregaRepository.findForUpdateByBdoId(jaCancelada))
                .thenReturn(Optional.of(novaEntrega(UUID.randomUUID(), EntregaStatus.CANCELADA)));

        assertThatThrownBy(() -> entregaService.cancelar(concluida)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> entregaService.cancelar(jaCancelada)).isInstanceOf(ConflitoException.class);
        verify(entregaRepository, never()).save(any());
    }
}
