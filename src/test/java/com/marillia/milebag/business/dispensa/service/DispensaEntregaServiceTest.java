package com.marillia.milebag.business.dispensa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.dispensa.model.DispensaEntrega;
import com.marillia.milebag.business.dispensa.model.DispensaEntregaResponse;
import com.marillia.milebag.business.dispensa.model.DispensarEntregaRequest;
import com.marillia.milebag.business.dispensa.repository.DispensaEntregaRepository;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Regras da dispensa (V46): sem motivo so na primeira tentativa antes de retirar a bagagem; em rota a viagem e
 * encerrada (e paga); em nova tentativa o motivo e sempre exigido.
 */
@ExtendWith(MockitoExtension.class)
class DispensaEntregaServiceTest {

    @Mock
    private DispensaEntregaRepository dispensaEntregaRepository;
    @Mock
    private EntregaService entregaService;
    @Mock
    private ViagemService viagemService;
    @Mock
    private MotivoOcorrenciaService motivoOcorrenciaService;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;

    @InjectMocks
    private DispensaEntregaService dispensaEntregaService;

    private final UUID motoristaId = UUID.randomUUID();

    private Entrega entrega(EntregaStatus status, UUID responsavel) {
        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setStatus(status);
        entrega.setMotoristaResponsavel(responsavel);
        when(entregaService.buscarPorId(entrega.getId())).thenReturn(Optional.of(entrega));
        return entrega;
    }

    private void salvarDevolvendoComId() {
        when(dispensaEntregaRepository.save(any(DispensaEntrega.class))).thenAnswer(invocacao -> {
            DispensaEntrega dispensa = invocacao.getArgument(0);
            dispensa.setId(UUID.randomUUID());
            return dispensa;
        });
    }

    @Test
    void primeiraTentativaSemRetirarABagagemDispensaSemMotivo() {
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, motoristaId);
        when(viagemService.houveTentativa(entrega.getId())).thenReturn(false);
        when(viagemService.viagemEmAndamento(entrega.getId())).thenReturn(false);
        salvarDevolvendoComId();

        DispensaEntregaResponse resposta = dispensaEntregaService.dispensar(entrega.getId(), motoristaId, null);

        assertThat(resposta.comBagagem()).isFalse();
        assertThat(resposta.motivo()).isNull();
        verify(viagemService, never()).encerrarPorDispensa(any(), any());
        // Ninguem saiu com a bagagem: volta como entrega nova, nao como reaberta.
        verify(entregaService).devolverAposDispensa(entrega.getId(), motoristaId, false);
    }

    @Test
    void emRotaExigeMotivo() {
        Entrega entrega = entrega(EntregaStatus.EM_ANDAMENTO, motoristaId);
        when(viagemService.houveTentativa(entrega.getId())).thenReturn(true);
        when(viagemService.viagemEmAndamento(entrega.getId())).thenReturn(true);

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(entrega.getId(), motoristaId, new DispensarEntregaRequest(null, "sem motivo")))
                .isInstanceOf(RegraNegocioException.class);
        verify(dispensaEntregaRepository, never()).save(any());
        verify(entregaService, never()).devolverAposDispensa(any(), any(), anyBoolean());
    }

    @Test
    void emRotaComMotivoEncerraAViagemEDevolveComoReaberta() {
        Entrega entrega = entrega(EntregaStatus.EM_ANDAMENTO, motoristaId);
        UUID motivoId = UUID.randomUUID();
        when(viagemService.houveTentativa(entrega.getId())).thenReturn(true);
        when(viagemService.viagemEmAndamento(entrega.getId())).thenReturn(true);
        when(motivoOcorrenciaService.descricao(motivoId)).thenReturn(Optional.of("Problema no veículo"));
        salvarDevolvendoComId();

        DispensaEntregaResponse resposta = dispensaEntregaService.dispensar(
                entrega.getId(), motoristaId, new DispensarEntregaRequest(motivoId, "  pneu furado  "));

        verify(motivoOcorrenciaService).buscarAtivoOuFalhar(motivoId, FinalidadeMotivo.DISPENSA);
        ArgumentCaptor<DispensaEntrega> salva = ArgumentCaptor.forClass(DispensaEntrega.class);
        verify(dispensaEntregaRepository).save(salva.capture());
        assertThat(salva.getValue().isComBagagem()).isTrue();
        assertThat(salva.getValue().getComplemento()).isEqualTo("pneu furado");
        verify(viagemService).encerrarPorDispensa(entrega.getId(), resposta.id());
        verify(entregaService).devolverAposDispensa(entrega.getId(), motoristaId, true);
        assertThat(resposta.motivo()).isEqualTo("Problema no veículo");
    }

    @Test
    void novaTentativaAntesDeSairExigeMotivoMasNaoEncerraViagem() {
        // Entrega reaberta e reassumida: a viagem anterior ja foi encerrada e a atual ainda nao comecou.
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, motoristaId);
        when(viagemService.houveTentativa(entrega.getId())).thenReturn(true);
        when(viagemService.viagemEmAndamento(entrega.getId())).thenReturn(false);

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(entrega.getId(), motoristaId, null))
                .isInstanceOf(RegraNegocioException.class);

        UUID motivoId = UUID.randomUUID();
        salvarDevolvendoComId();
        dispensaEntregaService.dispensar(entrega.getId(), motoristaId, new DispensarEntregaRequest(motivoId, null));

        verify(viagemService, never()).encerrarPorDispensa(any(), any());
        verify(entregaService).devolverAposDispensa(entrega.getId(), motoristaId, true);
    }

    @Test
    void motivoDeOcorrenciaOuInativoNaoServeParaDispensa() {
        Entrega entrega = entrega(EntregaStatus.EM_ANDAMENTO, motoristaId);
        UUID motivoDeOcorrencia = UUID.randomUUID();
        when(viagemService.houveTentativa(entrega.getId())).thenReturn(true);
        when(viagemService.viagemEmAndamento(entrega.getId())).thenReturn(true);
        when(motivoOcorrenciaService.buscarAtivoOuFalhar(motivoDeOcorrencia, FinalidadeMotivo.DISPENSA))
                .thenThrow(new RecursoNaoEncontradoException("Motivo nao encontrado."));

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(
                entrega.getId(), motoristaId, new DispensarEntregaRequest(motivoDeOcorrencia, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(dispensaEntregaRepository, never()).save(any());
    }

    @Test
    void outroMotoristaNaoPodeDispensar() {
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, UUID.randomUUID());

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(entrega.getId(), motoristaId, null))
                .isInstanceOf(ConflitoException.class);
        verify(dispensaEntregaRepository, never()).save(any());
    }

    @Test
    void entregaConcluidaNaoPodeSerDispensada() {
        Entrega entrega = entrega(EntregaStatus.ENCERRADA_COM_SUCESSO, motoristaId);

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(entrega.getId(), motoristaId, null))
                .isInstanceOf(ConflitoException.class);
        verify(entregaService, never()).devolverAposDispensa(any(), any(), anyBoolean());
    }

    @Test
    void complementoLongoDemaisERecusado() {
        Entrega entrega = entrega(EntregaStatus.ATRIBUIDA, motoristaId);
        when(viagemService.houveTentativa(entrega.getId())).thenReturn(false);
        when(viagemService.viagemEmAndamento(entrega.getId())).thenReturn(false);
        String longo = "x".repeat(DispensaEntregaService.TAMANHO_MAXIMO_COMPLEMENTO + 1);

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(entrega.getId(), motoristaId, new DispensarEntregaRequest(null, longo)))
                .isInstanceOf(RegraNegocioException.class);
        verify(dispensaEntregaRepository, never()).save(any());
    }

    @Test
    void entregaConcluidaNoMesmoInstanteNaoGravaADispensa() {
        Entrega entrega = entrega(EntregaStatus.EM_ANDAMENTO, motoristaId);
        when(viagemService.houveTentativa(entrega.getId())).thenReturn(true);
        when(viagemService.viagemEmAndamento(entrega.getId())).thenReturn(true);
        org.mockito.Mockito.doThrow(new ConflitoException("mudou"))
                .when(entregaService).devolverAposDispensa(entrega.getId(), motoristaId, true);

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(
                entrega.getId(), motoristaId, new DispensarEntregaRequest(UUID.randomUUID(), null)))
                .isInstanceOf(ConflitoException.class);
        verify(dispensaEntregaRepository, never()).save(any());
        verify(viagemService, never()).encerrarPorDispensa(any(), any());
    }

    @Test
    void historicoSoParaEmpresaResponsavelOuEntregaDisponivel() {
        Entrega deOutro = entrega(EntregaStatus.ATRIBUIDA, UUID.randomUUID());
        Entrega disponivel = entrega(EntregaStatus.REABERTA, null);
        Entrega minha = entrega(EntregaStatus.ATRIBUIDA, motoristaId);
        when(dispensaEntregaRepository.findByEntregaIdOrderByDataHoraAsc(any())).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> dispensaEntregaService.listarParaUsuario(deOutro.getId(), motoristaId, "MOTORISTA"))
                .isInstanceOf(com.marillia.milebag.core.exception.AcessoNegadoException.class);
        assertThat(dispensaEntregaService.listarParaUsuario(disponivel.getId(), motoristaId, "MOTORISTA")).isEmpty();
        assertThat(dispensaEntregaService.listarParaUsuario(minha.getId(), motoristaId, "MOTORISTA")).isEmpty();
        assertThat(dispensaEntregaService.listarParaUsuario(deOutro.getId(), UUID.randomUUID(), "ADMINISTRADOR_TENANT")).isEmpty();
    }

    @Test
    void exigeMotivoQuandoAEntregaJaTeveViagem() {
        UUID comViagem = UUID.randomUUID();
        UUID semViagem = UUID.randomUUID();
        when(viagemService.houveTentativa(comViagem)).thenReturn(true);
        when(viagemService.houveTentativa(eq(semViagem))).thenReturn(false);

        assertThat(dispensaEntregaService.exigeMotivo(comViagem)).isTrue();
        assertThat(dispensaEntregaService.exigeMotivo(semViagem)).isFalse();
    }

    /** V47: com a tela aberta depois do cancelamento, o motorista ve o motivo da saida, nao "so o responsavel...". */
    @Test
    void entregaCanceladaPelaEmpresaMostraAMensagemDoCancelamento() {
        Entrega entrega = entrega(EntregaStatus.CANCELADA, null);

        assertThatThrownBy(() -> dispensaEntregaService.dispensar(entrega.getId(), motoristaId, null))
                .isInstanceOf(ConflitoException.class)
                .hasMessage(com.marillia.milebag.business.entrega.service.EntregaService.MENSAGEM_CANCELADA);
        verify(dispensaEntregaRepository, never()).save(any());
    }
}
