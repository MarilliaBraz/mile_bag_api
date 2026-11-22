package com.marillia.milebag.business.viagem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.viagem.model.PosicaoViagem;
import com.marillia.milebag.business.viagem.model.RegistrarPosicoesViagemRequest;
import com.marillia.milebag.business.viagem.model.RegistrarPosicoesViagemRequest.Posicao;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.repository.PosicaoViagemRepository;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.geo.RoutingClient;

/** Item 9: pontos de GPS da viagem, enviados em lote (tambem os guardados sem internet). */
@ExtendWith(MockitoExtension.class)
class PosicaoViagemServiceTest {

    private static final UUID MOTORISTA = UUID.randomUUID();
    private static final UUID ENTREGA = UUID.randomUUID();
    /** Seis horas atras: todos os pontos dos testes sao do passado (ponto "no futuro" e descartado). */
    private static final Instant INICIO = Instant.now().minusSeconds(6 * 3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private static final double GRAU_POR_100M = 100 / 111_195.0;

    @Mock
    private PosicaoViagemRepository posicaoViagemRepository;
    @Mock
    private TrajetoRepository trajetoRepository;
    @Mock
    private RoutingClient routingClient;

    private PosicaoViagemService service;
    private final List<PosicaoViagem> gravadas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PosicaoViagemService(posicaoViagemRepository, trajetoRepository, routingClient);
        lenient().when(posicaoViagemRepository.save(any(PosicaoViagem.class))).thenAnswer(chamada -> {
            gravadas.add(chamada.getArgument(0));
            return chamada.getArgument(0);
        });
        lenient().when(posicaoViagemRepository.findByTrajetoIdOrderByDataHoraAsc(any())).thenAnswer(chamada -> gravadas);
        lenient().when(posicaoViagemRepository.idsLocaisJaRegistrados(anyCollection(), anyCollection())).thenReturn(List.of());
    }

    private Trajeto trajeto(UUID motorista, Instant inicio) {
        Trajeto trajeto = new Trajeto(ENTREGA, motorista, inicio);
        trajeto.setId(UUID.randomUUID());
        return trajeto;
    }

    private static Posicao ponto(int indice, double metrosAoNorte, long segundos) {
        return new Posicao("p" + indice, ENTREGA, -16.6 + metrosAoNorte / 100 * GRAU_POR_100M, -49.2, 8.0, INICIO.plusSeconds(segundos));
    }

    private static RegistrarPosicoesViagemRequest lote(Posicao... posicoes) {
        return new RegistrarPosicoesViagemRequest(List.of(posicoes));
    }

    @Test
    void gravaOsPontosNaViagemDaEntregaERecalculaOKm() {
        Trajeto viagem = trajeto(MOTORISTA, INICIO);
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(viagem));

        var resposta = service.registrar(MOTORISTA, lote(ponto(1, 0, 10), ponto(2, 300, 25), ponto(3, 600, 40)));

        assertThat(resposta.aceitas()).isEqualTo(3);
        assertThat(resposta.pendentes()).isEmpty();
        assertThat(gravadas).allMatch(p -> p.getTrajetoId().equals(viagem.getId()));
        assertThat(viagem.getKmPercorrido()).isEqualByComparingTo(new BigDecimal("0.600"));
        verify(trajetoRepository).save(viagem);
    }

    @Test
    void pontoRepetidoNaoEntraDuasVezes() {
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(trajeto(MOTORISTA, INICIO)));
        when(posicaoViagemRepository.idsLocaisJaRegistrados(anyCollection(), anyCollection())).thenReturn(List.of("p1"));

        var resposta = service.registrar(MOTORISTA, lote(ponto(1, 0, 10), ponto(2, 300, 25), ponto(2, 300, 25)));

        assertThat(resposta.aceitas()).isEqualTo(1);
        assertThat(gravadas).extracting(PosicaoViagem::getIdLocal).containsExactly("p2");
    }

    @Test
    void semViagemNoServidorOPontoFicaPendente() {
        // A viagem foi iniciada sem internet e ainda nao sincronizou.
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of());

        var resposta = service.registrar(MOTORISTA, lote(ponto(1, 0, 10)));

        assertThat(resposta.aceitas()).isZero();
        assertThat(resposta.pendentes()).containsExactly("p1");
        verify(posicaoViagemRepository, never()).save(any());
    }

    @Test
    void viagemIniciadaSemInternetRecebeOsPontosDeAntesDaSincronizacao() {
        // No servidor a viagem comeca na hora da sincronizacao, 20 min depois dos primeiros pontos.
        Trajeto viagem = trajeto(MOTORISTA, INICIO.plusSeconds(1_200));
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(viagem));

        var resposta = service.registrar(MOTORISTA, lote(ponto(1, 0, 10)));

        assertThat(resposta.aceitas()).isEqualTo(1);
    }

    @Test
    void viagemDeOutroMotoristaNaoRecebeOsPontos() {
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(trajeto(UUID.randomUUID(), INICIO)));

        var resposta = service.registrar(MOTORISTA, lote(ponto(1, 0, 10)));

        assertThat(resposta.aceitas()).isZero();
        assertThat(resposta.pendentes()).containsExactly("p1");
    }

    @Test
    void pontoInvalidoEDescartadoSemTravarOLote() {
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(trajeto(MOTORISTA, INICIO)));
        Posicao foraDoMapa = new Posicao("x", ENTREGA, 120.0, -49.2, 8.0, INICIO.plusSeconds(5));
        Posicao noFuturo = new Posicao("y", ENTREGA, -16.6, -49.2, 8.0, Instant.now().plusSeconds(3_600));

        var resposta = service.registrar(MOTORISTA, lote(foraDoMapa, noFuturo, ponto(1, 0, 10)));

        assertThat(resposta.aceitas()).isEqualTo(1);
        assertThat(resposta.pendentes()).isEmpty();
    }

    @Test
    void loteGrandeDemaisERecusado() {
        Posicao[] muitos = IntStream.range(0, PosicaoViagemService.LOTE_MAXIMO + 1).mapToObj(i -> ponto(i, i, i)).toArray(Posicao[]::new);

        assertThatThrownBy(() -> service.registrar(MOTORISTA, lote(muitos))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void lacunaUsaADistanciaPelaEstrada() {
        Trajeto viagem = trajeto(MOTORISTA, INICIO);
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(viagem));
        when(routingClient.calcularAlternativas(any(), any())).thenReturn(List.of(
                new com.marillia.milebag.core.geo.RotaAlternativa("a", 8_000, 600, false, List.of())));

        service.registrar(MOTORISTA, lote(ponto(1, 0, 10), ponto(2, 6_000, 610)));

        assertThat(viagem.getKmPercorrido()).isEqualByComparingTo(new BigDecimal("8.000"));
        ArgumentCaptor<com.marillia.milebag.core.geo.Coordenada> origem = ArgumentCaptor.forClass(com.marillia.milebag.core.geo.Coordenada.class);
        verify(routingClient).calcularAlternativas(origem.capture(), any());
        assertThat(origem.getValue().latitude()).isEqualTo(-16.6);
    }

    @Test
    void pontoDaTentativaAnteriorNaoVaiParaANovaTentativa() {
        // Tentativa 1 das 0h as 1h (ocorrencia); tentativa 2 comeca 2h depois do inicio.
        Trajeto primeira = trajeto(MOTORISTA, INICIO);
        primeira.consolidar(BigDecimal.TEN, BigDecimal.TEN, null, 600, null);
        org.springframework.test.util.ReflectionTestUtils.setField(primeira, "encerradoEm", INICIO.plusSeconds(3600));
        Trajeto segunda = trajeto(MOTORISTA, INICIO.plusSeconds(2 * 3600));
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(primeira, segunda));

        service.registrar(MOTORISTA, lote(ponto(1, 0, 600), ponto(2, 300, 615), ponto(3, 0, 2 * 3600 + 30)));

        assertThat(gravadas).extracting(PosicaoViagem::getTrajetoId).containsExactly(primeira.getId(), primeira.getId(), segunda.getId());
    }

    @Test
    void viagemOfflinePorMaisDeDuasHorasRecebeTodoOCaminho() {
        // A viagem so chegou ao servidor 5 h depois do primeiro ponto.
        Trajeto viagem = trajeto(MOTORISTA, INICIO.plusSeconds(5 * 3600));
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(viagem));

        var resposta = service.registrar(MOTORISTA, lote(ponto(1, 0, 10)));

        assertThat(resposta.aceitas()).isEqualTo(1);
    }

    @Test
    void pontoDeMaisDeUmDiaAntesDaViagemFicaDeFora() {
        Trajeto viagem = trajeto(MOTORISTA, Instant.now());
        when(trajetoRepository.findByEntregaIdParaAtualizar(ENTREGA)).thenReturn(List.of(viagem));
        Posicao antigo = new Posicao("velho", ENTREGA, -16.6, -49.2, 8.0, Instant.now().minusSeconds(26 * 3600));

        var resposta = service.registrar(MOTORISTA, lote(antigo));

        assertThat(resposta.aceitas()).isZero();
        assertThat(resposta.pendentes()).containsExactly("velho");
    }
}

