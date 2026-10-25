package com.marillia.milebag.business.ocorrencia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.ocorrencia.model.Ocorrencia;
import com.marillia.milebag.business.ocorrencia.repository.OcorrenciaRepository;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Cobre o refactor de acoplamento em OcorrenciaService: consulta o motivo
 * via MotivoOcorrenciaService (antes MotivoOcorrenciaRepository direto) e
 * reabre a Entrega via EntregaService (antes escrevia status/motorista
 * direto e chamava EntregaRepository.save por fora do dominio dela).
 */
@ExtendWith(MockitoExtension.class)
class OcorrenciaServiceTest {

    @Mock
    private OcorrenciaRepository ocorrenciaRepository;
    @Mock
    private MotivoOcorrenciaService motivoOcorrenciaService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private MensagemService mensagemService;
    @Mock
    private ViagemService viagemService;

    @InjectMocks
    private OcorrenciaService ocorrenciaService;

    private Entrega entregaComStatus(EntregaStatus status) {
        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setStatus(status);
        return entrega;
    }

    @Test
    void registrarBuscaMotivoAtivoEReabreEntregaViaServicos() {
        UUID entregaId = UUID.randomUUID();
        UUID motivoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComStatus(EntregaStatus.EM_ANDAMENTO)));
        MotivoOcorrencia motivo = new MotivoOcorrencia("Endereco nao localizado");
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId)).thenReturn(motivo);
        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenAnswer(inv -> inv.getArgument(0));

        ocorrenciaService.registrar(entregaId, motivoId, "ninguem atendeu", null, null);

        verify(motivoOcorrenciaService).buscarAtivoOuFalhar(motivoId);
        verify(entregaService).reabrirParaNovaTentativa(entregaId, null);
        verify(mensagemService).notificarAutomatica(entregaId, TipoEventoNotificacao.OCORRENCIA_REGISTRADA);
        // A viagem desta tentativa e encerrada e vinculada a ocorrencia (paga a quem dirigiu).
        verify(viagemService).encerrarPorOcorrencia(eq(entregaId), any());
    }

    @Test
    void registrarLancaConflitoQuandoEntregaJaEncerradaComSucesso() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId))
                .thenReturn(Optional.of(entregaComStatus(EntregaStatus.ENCERRADA_COM_SUCESSO)));

        assertThatThrownBy(() -> ocorrenciaService.registrar(entregaId, UUID.randomUUID(), "desc", null, null))
                .isInstanceOf(ConflitoException.class);
        verify(motivoOcorrenciaService, never()).buscarAtivoOuFalhar(any());
        verify(entregaService, never()).reabrirParaNovaTentativa(any(), any());
    }

    @Test
    void registrarLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ocorrenciaService.registrar(entregaId, UUID.randomUUID(), "desc", null, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void reenvioComOMesmoIdLocalNaoCriaNadaEInformaQueNaoEUmRegistroNovo() {
        UUID entregaId = UUID.randomUUID();
        UUID motivoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComStatus(EntregaStatus.REABERTA)));
        Ocorrencia existente = new Ocorrencia(entregaId, motivoId, "ninguem atendeu", Instant.now(), true, "local-1");
        when(ocorrenciaRepository.findByEntregaIdAndIdLocal(entregaId, "local-1")).thenReturn(Optional.of(existente));

        var resultado = ocorrenciaService.registrarComResultado(entregaId, motivoId, "ninguem atendeu", "local-1", null);

        assertThat(resultado.novo()).isFalse();
        verify(ocorrenciaRepository, never()).save(any());
        verify(entregaService, never()).reabrirParaNovaTentativa(any(), any());
        verify(mensagemService, never()).notificarAutomatica(any(), any());
    }

    @Test
    void registroNovoInformaNovoVerdadeiro() {
        UUID entregaId = UUID.randomUUID();
        UUID motivoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComStatus(EntregaStatus.EM_ANDAMENTO)));
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId)).thenReturn(new MotivoOcorrencia("Endereco nao localizado"));
        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(ocorrenciaService.registrarComResultado(entregaId, motivoId, null, "local-2", null).novo()).isTrue();
    }

    // --- HU29: reagendamento ---

    private static final Instant INICIO = Instant.parse("2026-10-23T17:00:00Z");
    private static final Instant FIM = Instant.parse("2026-10-23T19:00:00Z");

    @Test
    void reagendamentoGuardaJanelaNaOcorrenciaENaEntregaEAvisaOPassageiro() {
        UUID entregaId = UUID.randomUUID();
        UUID motivoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComStatus(EntregaStatus.EM_ANDAMENTO)));
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId)).thenReturn(new MotivoOcorrencia("Reagendada", true));
        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenAnswer(inv -> inv.getArgument(0));
        JanelaReagendamento janela = new JanelaReagendamento(INICIO, FIM);

        var resposta = ocorrenciaService.registrar(entregaId, motivoId, null, null, janela);

        assertThat(resposta.janelaInicio()).isEqualTo(INICIO);
        assertThat(resposta.janelaFim()).isEqualTo(FIM);
        verify(entregaService).reabrirParaNovaTentativa(entregaId, janela);
        verify(mensagemService).notificarAutomatica(entregaId, TipoEventoNotificacao.REAGENDAMENTO_REGISTRADO);
    }

    @Test
    void motivoDeReagendamentoSemJanelaERecusado() {
        UUID entregaId = UUID.randomUUID();
        UUID motivoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComStatus(EntregaStatus.EM_ANDAMENTO)));
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId)).thenReturn(new MotivoOcorrencia("Reagendada", true));

        assertThatThrownBy(() -> ocorrenciaService.registrar(entregaId, motivoId, null, null, null))
                .isInstanceOf(RegraNegocioException.class);
        verify(ocorrenciaRepository, never()).save(any());
        verify(entregaService, never()).reabrirParaNovaTentativa(any(), any());
    }

    @Test
    void janelaEmMotivoQueNaoEReagendamentoERecusada() {
        UUID entregaId = UUID.randomUUID();
        UUID motivoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComStatus(EntregaStatus.EM_ANDAMENTO)));
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoId)).thenReturn(new MotivoOcorrencia("Recusa de recebimento"));

        assertThatThrownBy(() -> ocorrenciaService.registrar(entregaId, motivoId, null, null, new JanelaReagendamento(INICIO, FIM)))
                .isInstanceOf(RegraNegocioException.class);
        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    void janelaIncompletaOuInvertidaERecusadaAntesDeChegarAoServico() {
        assertThatThrownBy(() -> JanelaReagendamento.deCampos(INICIO, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> JanelaReagendamento.deCampos(FIM, INICIO)).isInstanceOf(RegraNegocioException.class);
        assertThat(JanelaReagendamento.deCampos(null, null)).isNull();
    }
}
