package com.marillia.milebag.business.configuracao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.repository.MotivoOcorrenciaRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Cobre {@link MotivoOcorrenciaService#buscarAtivoOuFalhar}, o ponto de
 * leitura publico adicionado no refactor de acoplamento para que
 * OcorrenciaService pare de injetar MotivoOcorrenciaRepository direto.
 */
@ExtendWith(MockitoExtension.class)
class MotivoOcorrenciaServiceTest {

    @Mock
    private MotivoOcorrenciaRepository motivoOcorrenciaRepository;

    @InjectMocks
    private MotivoOcorrenciaService motivoOcorrenciaService;

    @Test
    void buscarAtivoOuFalharRetornaMotivoAtivo() {
        UUID id = UUID.randomUUID();
        MotivoOcorrencia motivo = new MotivoOcorrencia("Endereco nao localizado");
        when(motivoOcorrenciaRepository.findById(id)).thenReturn(Optional.of(motivo));

        assertThat(motivoOcorrenciaService.buscarAtivoOuFalhar(id, FinalidadeMotivo.OCORRENCIA)).isSameAs(motivo);
    }

    @Test
    void buscarAtivoOuFalharLancaQuandoDesativado() {
        UUID id = UUID.randomUUID();
        MotivoOcorrencia motivo = new MotivoOcorrencia("Endereco nao localizado");
        motivo.definirAtivo(false);
        when(motivoOcorrenciaRepository.findById(id)).thenReturn(Optional.of(motivo));

        assertThatThrownBy(() -> motivoOcorrenciaService.buscarAtivoOuFalhar(id, FinalidadeMotivo.OCORRENCIA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void buscarAtivoOuFalharLancaQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(motivoOcorrenciaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> motivoOcorrenciaService.buscarAtivoOuFalhar(id, FinalidadeMotivo.OCORRENCIA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    /** Empresa sem nenhum motivo recebe os dois catalogos padrao (ocorrencia, com reagendamento, e dispensa) no tenant indicado (RLS). */
    @Test
    void criarPadroesSeAusentesCriaCatalogoNoTenantIndicado() {
        UUID tenantId = UUID.randomUUID();
        UUID[] tenantNoSave = new UUID[1];
        when(motivoOcorrenciaRepository.existsByFinalidade(any())).thenReturn(false);
        when(motivoOcorrenciaRepository.save(any(MotivoOcorrencia.class))).thenAnswer(invocacao -> {
            tenantNoSave[0] = TenantContext.get();
            return invocacao.getArgument(0);
        });

        motivoOcorrenciaService.criarPadroesSeAusentes(tenantId);

        ArgumentCaptor<MotivoOcorrencia> salvos = ArgumentCaptor.forClass(MotivoOcorrencia.class);
        verify(motivoOcorrenciaRepository, times(22)).save(salvos.capture());
        List<MotivoOcorrencia> motivos = salvos.getAllValues();
        assertThat(motivos).extracting(MotivoOcorrencia::getDescricao).contains("Ausência do recebedor", "Reagendada com o passageiro");
        assertThat(motivos).filteredOn(MotivoOcorrencia::isReagendamento).hasSize(1)
                .allMatch(motivo -> motivo.getFinalidade() == FinalidadeMotivo.OCORRENCIA);
        assertThat(motivos).filteredOn(motivo -> motivo.getFinalidade() == FinalidadeMotivo.DISPENSA)
                .extracting(MotivoOcorrencia::getDescricao).contains("Problema no veículo", "Outro motivo").hasSize(4);
        assertThat(motivos).filteredOn(motivo -> motivo.getFinalidade() == FinalidadeMotivo.CANCELAMENTO)
                .extracting(MotivoOcorrencia::getDescricao).contains("Enviado por engano", "BDO duplicado").hasSize(4);
        assertThat(motivos).filteredOn(motivo -> motivo.getFinalidade() == FinalidadeMotivo.RECEBEDOR)
                .extracting(MotivoOcorrencia::getDescricao).contains("Titular", "Porteiro ou portaria").hasSize(5);
        assertThat(tenantNoSave[0]).isEqualTo(tenantId);
        assertThat(TenantContext.get()).isNull();
    }

    /** Idempotente: empresa que ja tem motivos das duas finalidades (mesmo desativados) nao ganha duplicatas. */
    @Test
    void criarPadroesSeAusentesNaoFazNadaQuandoJaHaMotivos() {
        when(motivoOcorrenciaRepository.existsByFinalidade(any())).thenReturn(true);

        motivoOcorrenciaService.criarPadroesSeAusentes(UUID.randomUUID());

        verify(motivoOcorrenciaRepository, never()).save(any());
        assertThat(TenantContext.get()).isNull();
    }

    /** Empresa existente que so tinha motivos de ocorrencia recebe apenas os de dispensa. */
    @Test
    void criarPadroesSeAusentesCompletaSoAFinalidadeQueFalta() {
        when(motivoOcorrenciaRepository.existsByFinalidade(FinalidadeMotivo.OCORRENCIA)).thenReturn(true);
        when(motivoOcorrenciaRepository.existsByFinalidade(FinalidadeMotivo.DISPENSA)).thenReturn(false);
        when(motivoOcorrenciaRepository.existsByFinalidade(FinalidadeMotivo.CANCELAMENTO)).thenReturn(true);
        when(motivoOcorrenciaRepository.existsByFinalidade(FinalidadeMotivo.RECEBEDOR)).thenReturn(true);
        when(motivoOcorrenciaRepository.existsByFinalidade(FinalidadeMotivo.CORRECAO)).thenReturn(true);
        when(motivoOcorrenciaRepository.save(any(MotivoOcorrencia.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        motivoOcorrenciaService.criarPadroesSeAusentes(UUID.randomUUID());

        ArgumentCaptor<MotivoOcorrencia> salvos = ArgumentCaptor.forClass(MotivoOcorrencia.class);
        verify(motivoOcorrenciaRepository, times(4)).save(salvos.capture());
        assertThat(salvos.getAllValues()).allMatch(motivo -> motivo.getFinalidade() == FinalidadeMotivo.DISPENSA);
    }

    /** Um motivo de dispensa nao serve para registrar ocorrencia (e vice-versa): tratado como inexistente. */
    @Test
    void buscarAtivoOuFalharRecusaMotivoDeOutraFinalidade() {
        UUID id = UUID.randomUUID();
        when(motivoOcorrenciaRepository.findById(id))
                .thenReturn(Optional.of(new MotivoOcorrencia("Problema no veículo", false, FinalidadeMotivo.DISPENSA)));

        assertThatThrownBy(() -> motivoOcorrenciaService.buscarAtivoOuFalhar(id, FinalidadeMotivo.OCORRENCIA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    /** Reagendamento so faz sentido em motivo de ocorrencia. */
    @Test
    void criarRecusaReagendamentoEmMotivoDeDispensa() {
        assertThatThrownBy(() -> motivoOcorrenciaService.criar("Remarcar", true, FinalidadeMotivo.DISPENSA))
                .isInstanceOf(RegraNegocioException.class);
        verify(motivoOcorrenciaRepository, never()).save(any());
    }
}
