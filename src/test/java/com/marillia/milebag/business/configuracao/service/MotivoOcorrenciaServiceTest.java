package com.marillia.milebag.business.configuracao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.repository.MotivoOcorrenciaRepository;
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
}
