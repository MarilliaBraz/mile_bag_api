package com.marillia.milebag.business.pedagio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.pedagio.model.PracaPedagio;
import com.marillia.milebag.business.pedagio.model.PracaPedagioResponse;
import com.marillia.milebag.business.pedagio.model.PracaPedagioValor;
import com.marillia.milebag.business.pedagio.repository.PracaPedagioRepository;
import com.marillia.milebag.business.pedagio.repository.PracaPedagioValorRepository;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.geo.GeocodingClient;

/** Ajustes de 2026-11-22: renomear a praca e guardar a cidade da localizacao (V57). */
@ExtendWith(MockitoExtension.class)
class PracaPedagioServiceTest {

    @Mock
    private PracaPedagioRepository pracaRepository;
    @Mock
    private PracaPedagioValorRepository valorRepository;
    @Mock
    private GeocodingClient geocodingClient;
    @InjectMocks
    private PracaPedagioService service;

    @Test
    void renomearTrocaONomeSemEspacosDasPontas() {
        PracaPedagio praca = new PracaPedagio("Praca errada");
        UUID id = UUID.randomUUID();
        when(pracaRepository.findById(id)).thenReturn(Optional.of(praca));

        PracaPedagioResponse resposta = service.renomear(id, "  Praca Itupeva  ");

        assertThat(resposta.nome()).isEqualTo("Praca Itupeva");
        verify(pracaRepository).save(praca);
    }

    @Test
    void renomearRecusaNomeVazio() {
        assertThatThrownBy(() -> service.renomear(UUID.randomUUID(), "  ")).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(pracaRepository);
    }

    @Test
    void renomearRecusaPracaInexistente() {
        UUID id = UUID.randomUUID();
        when(pracaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renomear(id, "Nova")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void definirLocalizacaoGuardaACidadeDoMapa() {
        PracaPedagio praca = new PracaPedagio("Praca Itupeva");
        UUID id = UUID.randomUUID();
        when(pracaRepository.findById(id)).thenReturn(Optional.of(praca));
        when(geocodingClient.cidadeEm(-23.15, -47.06)).thenReturn(Optional.of("Itupeva/SP"));

        PracaPedagioResponse resposta = service.definirLocalizacao(id, -23.15, -47.06);

        assertThat(resposta.cidade()).isEqualTo("Itupeva/SP");
    }

    @Test
    void localizacaoSalvaMesmoSemACidade() {
        PracaPedagio praca = new PracaPedagio("Praca Itupeva");
        UUID id = UUID.randomUUID();
        when(pracaRepository.findById(id)).thenReturn(Optional.of(praca));
        when(geocodingClient.cidadeEm(-23.15, -47.06)).thenReturn(Optional.empty());

        PracaPedagioResponse resposta = service.definirLocalizacao(id, -23.15, -47.06);

        assertThat(resposta.latitude()).isEqualTo(-23.15);
        assertThat(resposta.cidade()).isNull();
    }

    @Test
    void removerLocalizacaoApagaACidadeSemConsultarOMapa() {
        PracaPedagio praca = new PracaPedagio("Praca Itupeva");
        praca.definirLocalizacao(-23.15, -47.06, "Itupeva/SP");
        UUID id = UUID.randomUUID();
        when(pracaRepository.findById(id)).thenReturn(Optional.of(praca));

        PracaPedagioResponse resposta = service.definirLocalizacao(id, null, null);

        assertThat(resposta.cidade()).isNull();
        verify(geocodingClient, never()).cidadeEm(any(Double.class), any(Double.class));
    }

    @Test
    void criarPracaComLocalizacaoJaGuardaACidade() {
        when(geocodingClient.cidadeEm(-23.15, -47.06)).thenReturn(Optional.of("Itupeva/SP"));
        when(pracaRepository.save(any(PracaPedagio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(valorRepository.save(any(PracaPedagioValor.class))).thenAnswer(inv -> inv.getArgument(0));

        PracaPedagioResponse resposta =
                service.criarPraca(" Praca Itupeva ", new BigDecimal("12.40"), LocalDate.of(2026, 11, 1), -23.15, -47.06);

        assertThat(resposta.nome()).isEqualTo("Praca Itupeva");
        assertThat(resposta.cidade()).isEqualTo("Itupeva/SP");
    }
}
