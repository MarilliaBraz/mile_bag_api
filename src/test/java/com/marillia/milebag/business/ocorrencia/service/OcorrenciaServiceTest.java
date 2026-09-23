package com.marillia.milebag.business.ocorrencia.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.ocorrencia.model.Ocorrencia;
import com.marillia.milebag.business.ocorrencia.repository.OcorrenciaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

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

        ocorrenciaService.registrar(entregaId, motivoId, "ninguem atendeu", null);

        verify(motivoOcorrenciaService).buscarAtivoOuFalhar(motivoId);
        verify(entregaService).reabrirParaNovaTentativa(entregaId);
        verify(mensagemService).notificarAutomatica(entregaId, TipoEventoNotificacao.OCORRENCIA_REGISTRADA);
    }

    @Test
    void registrarLancaConflitoQuandoEntregaJaEncerradaComSucesso() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId))
                .thenReturn(Optional.of(entregaComStatus(EntregaStatus.ENCERRADA_COM_SUCESSO)));

        assertThatThrownBy(() -> ocorrenciaService.registrar(entregaId, UUID.randomUUID(), "desc", null))
                .isInstanceOf(ConflitoException.class);
        verify(motivoOcorrenciaService, never()).buscarAtivoOuFalhar(any());
        verify(entregaService, never()).reabrirParaNovaTentativa(any());
    }

    @Test
    void registrarLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ocorrenciaService.registrar(entregaId, UUID.randomUUID(), "desc", null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
