package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.repository.AeroportoRepository;

class AeroportoServiceTest {

    private static final Aeroporto GRU = new Aeroporto("GRU", "Guarulhos", "São Paulo", "SP", -23.431274, -46.469954);
    private static final Aeroporto CGH = new Aeroporto("CGH", "Congonhas", "São Paulo", "SP", -23.627657, -46.655145);
    private static final Aeroporto GYN = new Aeroporto("GYN", "Santa Genoveva", "Goiânia", "GO", -16.632000, -49.220699);

    private final AeroportoRepository repository = mock(AeroportoRepository.class);
    private final AeroportoService service = new AeroportoService(repository);

    @Test
    void escolheOMaisProximoComADistancia() {
        when(repository.findAllByOrderByCodigoAsc()).thenReturn(List.of(CGH, GRU, GYN));

        // Terminal de Guarulhos: ~1 km do ponto de referencia do aeroporto.
        AeroportoService.AeroportoProximo proximo = service.maisProximo(-23.4356, -46.4731).orElseThrow();

        assertThat(proximo.aeroporto().getCodigo()).isEqualTo("GRU");
        assertThat(proximo.distanciaMetros()).isBetween(300, 1_500);
    }

    @Test
    void distanciaGeodesicaConhecida() {
        // GRU -> CGH: cerca de 29 km em linha reta.
        assertThat(AeroportoService.distanciaMetros(GRU.getLatitude(), GRU.getLongitude(), CGH.getLatitude(), CGH.getLongitude()))
                .isCloseTo(29_000, within(1_500.0));
    }

    @Test
    void carregaATabelaUmaVezSo() {
        when(repository.findAllByOrderByCodigoAsc()).thenReturn(List.of(GYN));

        service.listar();
        service.codigos();
        service.buscar("gyn");

        verify(repository, times(1)).findAllByOrderByCodigoAsc();
        assertThat(service.buscar("gyn")).contains(GYN);
    }
}
