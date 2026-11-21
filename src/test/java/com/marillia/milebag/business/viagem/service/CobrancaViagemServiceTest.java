package com.marillia.milebag.business.viagem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.service.AeroportoService;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.business.configuracao.service.BaseAeroportuariaService;
import com.marillia.milebag.business.viagem.model.RegraCobranca;
import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.GeocodingClient;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.RoutingClient;

/** Item 7 (V52): base do BDO, regra (fixo na cidade da base, km fora) e ida e volta a partir do aeroporto. */
@ExtendWith(MockitoExtension.class)
class CobrancaViagemServiceTest {

    @Mock
    private BaseAeroportuariaService baseAeroportuariaService;
    @Mock
    private AeroportoService aeroportoService;
    @Mock
    private RoutingClient routingClient;
    @Mock
    private GeocodingClient geocodingClient;

    private CobrancaViagemService servico;
    private final Aeroporto goiania = new Aeroporto("GYN", "Santa Genoveva", "Goiânia", "GO", -16.632, -49.2207);
    private final Aeroporto brasilia = new Aeroporto("BSB", "Juscelino Kubitschek", "Brasília", "DF", -15.869, -47.920);

    @BeforeEach
    void setUp() {
        servico = new CobrancaViagemService(baseAeroportuariaService, aeroportoService, routingClient, geocodingClient);
        org.mockito.Mockito.lenient().when(aeroportoService.buscar("GYN")).thenReturn(Optional.of(goiania));
        org.mockito.Mockito.lenient().when(aeroportoService.buscar("BSB")).thenReturn(Optional.of(brasilia));
    }

    private void bases(String principal, String... outras) {
        BaseAeroportuaria base = new BaseAeroportuaria(principal, null);
        base.definirPrincipal(true);
        List<BaseAeroportuaria> todas = new java.util.ArrayList<>(List.of(base));
        for (String outra : outras) {
            todas.add(new BaseAeroportuaria(outra, null));
        }
        when(baseAeroportuariaService.listar()).thenReturn(todas);
    }

    private static Bdo bdo(String aeroporto, String cidade, String endereco) {
        Bdo bdo = new Bdo();
        bdo.setAeroportoRetirada(aeroporto);
        bdo.setCidade(cidade);
        bdo.setEnderecoEntrega(endereco);
        return bdo;
    }

    @Test
    void naCidadeDaBasePagaFixoEForaPorKm() {
        bases("GYN");

        assertThat(servico.cobrancaDe(bdo("GYN", "GOIANIA", "RUA 1")).regra()).isEqualTo(RegraCobranca.FIXO_CIDADE_BASE);
        assertThat(servico.cobrancaDe(bdo("GYN", "APARECIDA DE GOIANIA", "RUA 1")).regra()).isEqualTo(RegraCobranca.POR_KM);
        assertThat(servico.cobrancaDe(bdo("GYN", "ANAPOLIS", "RUA 1")).regra()).isEqualTo(RegraCobranca.POR_KM);
    }

    @Test
    void aBaseEOAeroportoDoBdoSeForDaEmpresaSenaoAPrincipal() {
        bases("GYN", "BSB");

        assertThat(servico.baseDe(bdo("BSB", null, null))).contains(brasilia);
        assertThat(servico.baseDe(bdo("GRU", null, null))).contains(goiania);
        assertThat(servico.baseDe(bdo(null, null, null))).contains(goiania);
    }

    @Test
    void semBaseCadastradaPagaPorKm() {
        when(baseAeroportuariaService.listar()).thenReturn(List.of());

        CobrancaViagemService.Cobranca cobranca = servico.cobrancaDe(bdo("GYN", "GOIANIA", "RUA 1"));

        assertThat(cobranca.base()).isEmpty();
        assertThat(cobranca.regra()).isEqualTo(RegraCobranca.POR_KM);
    }

    /** BDO antigo, sem cidade separada: a cidade escrita no endereco; "Aparecida de Goiania" nao e Goiania. */
    @Test
    void bdoAntigoUsaACidadeEscritaNoEndereco() {
        bases("GYN");

        assertThat(servico.cobrancaDe(bdo(null, null, "Av. Goiás, 100, Goiânia - GO")).regra()).isEqualTo(RegraCobranca.FIXO_CIDADE_BASE);
        verify(geocodingClient, never()).cidadeDe(any());
    }

    @Test
    void semCidadeNoTextoPerguntaAoMapaUmaVezSo() {
        bases("GYN");
        when(geocodingClient.cidadeDe("RUA 5 QD 2 SETOR SUL")).thenReturn(Optional.of("Goiânia"));
        when(geocodingClient.cidadeDe("RUA 9, APARECIDA DE GOIANIA - GO")).thenReturn(Optional.of("Aparecida de Goiânia"));

        assertThat(servico.cobrancaDe(bdo(null, null, "RUA 5 QD 2 SETOR SUL")).regra()).isEqualTo(RegraCobranca.FIXO_CIDADE_BASE);
        assertThat(servico.cobrancaDe(bdo(null, null, "RUA 5 QD 2 SETOR SUL")).regra()).isEqualTo(RegraCobranca.FIXO_CIDADE_BASE);
        assertThat(servico.cobrancaDe(bdo(null, null, "RUA 9, APARECIDA DE GOIANIA - GO")).regra()).isEqualTo(RegraCobranca.POR_KM);
        verify(geocodingClient, org.mockito.Mockito.times(1)).cidadeDe("RUA 5 QD 2 SETOR SUL");
    }

    @Test
    void idaEVoltaCalculadasSeparadamenteAPartirDoAeroporto() {
        Coordenada destino = new Coordenada(-16.33, -48.95);
        when(routingClient.calcularAlternativas(any(), any())).thenAnswer(inv -> {
            Coordenada origem = inv.getArgument(0);
            double metros = origem.latitude() == goiania.getLatitude() ? 55_400 : 57_100;
            return List.of(new RotaAlternativa("a", metros + 3_000, 3600, false), new RotaAlternativa("b", metros, 3700, false));
        });

        Optional<BigDecimal[]> km = servico.kmIdaEVolta(goiania, destino);

        assertThat(km).isPresent();
        assertThat(km.get()[0]).isEqualByComparingTo("55.400");
        assertThat(km.get()[1]).isEqualByComparingTo("57.100");
    }

    @Test
    void motorDeRotasForaDevolveVazio() {
        when(routingClient.calcularAlternativas(any(), any())).thenReturn(List.of());

        assertThat(servico.kmIdaEVolta(goiania, new Coordenada(-16.33, -48.95))).isEmpty();
    }

    @Test
    void cidadeEscritaComoCidadeNaoComoParteDeOutroNome() {
        assertThat(CobrancaViagemService.enderecoNaCidade("RUA 1, GOIANIA - GO, 74000-100", "Goiânia")).isTrue();
        assertThat(CobrancaViagemService.enderecoNaCidade("RUA 1, GOIANIA, 74000-100", "Goiânia")).isTrue();
        assertThat(CobrancaViagemService.enderecoNaCidade("RUA 1, APARECIDA DE GOIANIA - GO", "Goiânia")).isFalse();
        assertThat(CobrancaViagemService.enderecoNaCidade("RUA GOIANIA 5, ANAPOLIS - GO", "Goiânia")).isFalse();
    }

    /** Mapa fora: a regra fica indeterminada (nunca vira "fora da cidade") e a falha nao e guardada no cache. */
    @Test
    void falhaDoMapaDeixaARegraIndeterminadaETentaDeNovo() {
        bases("GYN");
        when(geocodingClient.cidadeDe("RUA 7 SETOR NORTE"))
                .thenThrow(new com.marillia.milebag.core.geo.ServicoMapaIndisponivelException(new RuntimeException("429")))
                .thenReturn(Optional.of("Goiânia"));

        assertThat(servico.cobrancaDe(bdo(null, null, "RUA 7 SETOR NORTE")).regra()).isNull();
        assertThat(servico.cobrancaDe(bdo(null, null, "RUA 7 SETOR NORTE")).regra()).isEqualTo(RegraCobranca.FIXO_CIDADE_BASE);
    }

    @Test
    void cidadeHomonimaEmOutroEstadoNaoEACidadeDaBase() {
        bases("GYN");
        Bdo outroEstado = bdo("GYN", "GOIANIA", "RUA 1");
        outroEstado.setUf("MG");

        assertThat(servico.cobrancaDe(outroEstado).regra()).isEqualTo(RegraCobranca.POR_KM);
    }

    @Test
    void hifenEApostrofoNaoAtrapalhamAComparacao() {
        assertThat(CobrancaViagemService.normalizar("Mogi-Mirim")).isEqualTo(CobrancaViagemService.normalizar("Mogi Mirim"));
        assertThat(CobrancaViagemService.enderecoNaCidade("RUA 1, GOIANIA/GO", "Goiânia")).isTrue();
    }
}
