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

import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.repository.MotivoOcorrenciaRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

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

        assertThat(motivoOcorrenciaService.buscarAtivoOuFalhar(id)).isSameAs(motivo);
    }

    @Test
    void buscarAtivoOuFalharLancaQuandoDesativado() {
        UUID id = UUID.randomUUID();
        MotivoOcorrencia motivo = new MotivoOcorrencia("Endereco nao localizado");
        motivo.definirAtivo(false);
        when(motivoOcorrenciaRepository.findById(id)).thenReturn(Optional.of(motivo));

        assertThatThrownBy(() -> motivoOcorrenciaService.buscarAtivoOuFalhar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void buscarAtivoOuFalharLancaQuandoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(motivoOcorrenciaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> motivoOcorrenciaService.buscarAtivoOuFalhar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    /** Empresa sem nenhum motivo recebe o catalogo padrao, com o de reagendamento (HU29), no tenant indicado (RLS). */
    @Test
    void criarPadroesSeAusentesCriaCatalogoNoTenantIndicado() {
        UUID tenantId = UUID.randomUUID();
        UUID[] tenantNoSave = new UUID[1];
        when(motivoOcorrenciaRepository.count()).thenReturn(0L);
        when(motivoOcorrenciaRepository.save(any(MotivoOcorrencia.class))).thenAnswer(invocacao -> {
            tenantNoSave[0] = TenantContext.get();
            return invocacao.getArgument(0);
        });

        motivoOcorrenciaService.criarPadroesSeAusentes(tenantId);

        ArgumentCaptor<MotivoOcorrencia> salvos = ArgumentCaptor.forClass(MotivoOcorrencia.class);
        verify(motivoOcorrenciaRepository, times(4)).save(salvos.capture());
        List<MotivoOcorrencia> motivos = salvos.getAllValues();
        assertThat(motivos).extracting(MotivoOcorrencia::getDescricao).contains("Ausência do recebedor", "Reagendada com o passageiro");
        assertThat(motivos).filteredOn(MotivoOcorrencia::isReagendamento).hasSize(1);
        assertThat(tenantNoSave[0]).isEqualTo(tenantId);
        assertThat(TenantContext.get()).isNull();
    }

    /** Idempotente: empresa que ja tem motivos (mesmo desativados) nao ganha duplicatas. */
    @Test
    void criarPadroesSeAusentesNaoFazNadaQuandoJaHaMotivos() {
        when(motivoOcorrenciaRepository.count()).thenReturn(2L);

        motivoOcorrenciaService.criarPadroesSeAusentes(UUID.randomUUID());

        verify(motivoOcorrenciaRepository, never()).save(any());
        assertThat(TenantContext.get()).isNull();
    }
}
