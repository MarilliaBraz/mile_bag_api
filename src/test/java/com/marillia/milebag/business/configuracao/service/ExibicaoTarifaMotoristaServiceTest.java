package com.marillia.milebag.business.configuracao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.model.ExibicaoTarifaMotorista;
import com.marillia.milebag.business.configuracao.repository.ExibicaoTarifaMotoristaRepository;

/** V56: a empresa oculta ou mostra ao motorista a tarifa paga por viagem. */
@ExtendWith(MockitoExtension.class)
class ExibicaoTarifaMotoristaServiceTest {

    @Mock
    private ExibicaoTarifaMotoristaRepository repository;
    @InjectMocks
    private ExibicaoTarifaMotoristaService service;

    @Test
    void semConfiguracaoATarifaAparece() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.ocultarTarifa()).isFalse();
    }

    @Test
    void definirCriaAConfiguracaoQuandoNaoExiste() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any(ExibicaoTarifaMotorista.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.definir(true)).isTrue();

        ArgumentCaptor<ExibicaoTarifaMotorista> captor = ArgumentCaptor.forClass(ExibicaoTarifaMotorista.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isOcultarTarifa()).isTrue();
    }

    @Test
    void definirAtualizaAConfiguracaoExistente() {
        ExibicaoTarifaMotorista existente = new ExibicaoTarifaMotorista(true);
        when(repository.findAll()).thenReturn(List.of(existente));
        when(repository.save(any(ExibicaoTarifaMotorista.class))).thenAnswer(inv -> inv.getArgument(0));

        service.definir(false);

        verify(repository).save(existente);
        assertThat(existente.isOcultarTarifa()).isFalse();
    }
}
