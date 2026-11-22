package com.marillia.milebag.business.configuracao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.companhiaaerea.model.CompanhiaAerea;
import com.marillia.milebag.business.companhiaaerea.service.CompanhiaAereaService;
import com.marillia.milebag.business.configuracao.model.CompanhiaAereaAtendida;
import com.marillia.milebag.business.configuracao.repository.CompanhiaAereaAtendidaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class CompanhiaAereaAtendidaServiceTest {

    @Mock
    private CompanhiaAereaAtendidaRepository repository;
    @Mock
    private CompanhiaAereaService companhiaAereaService;
    @InjectMocks
    private CompanhiaAereaAtendidaService service;

    private static final CompanhiaAerea GOL = new CompanhiaAerea("G3", "GLO", "Gol Linhas Aéreas", "BR");

    @Test
    void companhiaForaDaListaERecusada() {
        when(companhiaAereaService.buscar("XX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar("XX", null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("nao esta na lista");
        verify(repository, never()).save(any());
    }

    @Test
    void codigoIcaoGravaOIataComONomeDaLista() {
        when(companhiaAereaService.buscar("glo")).thenReturn(Optional.of(GOL));
        when(repository.findByCodigoIata("G3")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanhiaAereaAtendida criada = service.criar("glo", " ");

        assertThat(criada.getCodigoIata()).isEqualTo("G3");
        assertThat(criada.getNome()).isEqualTo("Gol Linhas Aéreas");
    }

    @Test
    void nomeInformadoEMantido() {
        when(companhiaAereaService.buscar("G3")).thenReturn(Optional.of(GOL));
        when(repository.findByCodigoIata("G3")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.criar("G3", "GOL").getNome()).isEqualTo("GOL");
    }

    @Test
    void companhiaJaAtendidaDaConflito() {
        when(companhiaAereaService.buscar("G3")).thenReturn(Optional.of(GOL));
        when(repository.findByCodigoIata("G3")).thenReturn(Optional.of(new CompanhiaAereaAtendida("G3", "Gol")));

        assertThatThrownBy(() -> service.criar("G3", null)).isInstanceOf(ConflitoException.class);
    }
}
