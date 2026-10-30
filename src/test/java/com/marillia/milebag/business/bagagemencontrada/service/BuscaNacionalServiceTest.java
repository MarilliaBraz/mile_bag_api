package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaNacional;
import com.marillia.milebag.business.bagagemencontrada.model.BuscaNacionalResponse;
import com.marillia.milebag.business.bagagemencontrada.repository.AeroportoRepository;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaNacionalRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class BuscaNacionalServiceTest {

    @Mock
    private BagagemEncontradaNacionalRepository repository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private AeroportoRepository aeroportoRepository;

    private BuscaNacionalService service;
    private final Tenant minha = companhia("Linhas Aéreas Exemplo", "ZZ");
    private final Tenant outra = companhia("Outra Linhas Aéreas", "YY");

    @BeforeEach
    void setUp() {
        service = new BuscaNacionalService(repository, tenantRepository, new AeroportoService(aeroportoRepository));
        TenantContext.set(minha.getId());
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    void mostraAsDuasCompanhiasEOIdSoDaMinha() {
        when(aeroportoRepository.findAllByOrderByCodigoAsc())
                .thenReturn(List.of(new Aeroporto("GRU", "Guarulhos", "Guarulhos", "SP", -23.43, -46.47), new Aeroporto("GYN", "Santa Genoveva", "Goiânia", "GO", -16.63, -49.22)));
        BagagemEncontradaNacional daOutra =
                new BagagemEncontradaNacional(UUID.randomUUID(), outra.getId(), "0957123456", "GYN", Instant.parse("2026-10-29T18:00:00Z"), -16.63, -49.22);
        BagagemEncontradaNacional daMinha =
                new BagagemEncontradaNacional(UUID.randomUUID(), minha.getId(), "0957123456", "GRU", Instant.parse("2026-10-28T12:00:00Z"), null, null);
        when(repository.findTop50ByNumeroEtiquetaOrderByRegistradaEmDesc("0957123456")).thenReturn(List.of(daOutra, daMinha));
        when(tenantRepository.findAllById(any())).thenReturn(List.of(minha, outra));

        List<BuscaNacionalResponse> achadas = service.buscar(" 0957 123 456 ");

        assertThat(achadas).containsExactly(
                new BuscaNacionalResponse(
                        new BuscaNacionalResponse.Companhia("Outra Linhas Aéreas", "YY"), new BuscaNacionalResponse.Aeroporto("GYN", "Santa Genoveva", "Goiânia", "GO"),
                        Instant.parse("2026-10-29T18:00:00Z"), -16.63, -49.22, false, null),
                new BuscaNacionalResponse(
                        new BuscaNacionalResponse.Companhia("Linhas Aéreas Exemplo", "ZZ"), new BuscaNacionalResponse.Aeroporto("GRU", "Guarulhos", "Guarulhos", "SP"),
                        Instant.parse("2026-10-28T12:00:00Z"), null, null, true, daMinha.getBagagemId()));
    }

    @Test
    void aPlacaValeEmMinusculas() {
        when(repository.findTop50ByNumeroEtiquetaOrderByRegistradaEmDesc("LA123456")).thenReturn(List.of());

        assertThat(service.buscar("la123456")).isEmpty();
    }

    @Test
    void recusaNumeroForaDoFormato() {
        assertThatThrownBy(() -> service.buscar("12345")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.buscar("SID-GRU-2026-000002")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.buscar(null)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(repository);
    }

    private static Tenant companhia(String nome, String iata) {
        Tenant companhia = Tenant.companhiaAerea(iata.toLowerCase(), nome, iata);
        ReflectionTestUtils.setField(companhia, "id", UUID.randomUUID());
        return companhia;
    }
}
