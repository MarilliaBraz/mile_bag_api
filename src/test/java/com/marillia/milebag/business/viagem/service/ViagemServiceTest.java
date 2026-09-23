package com.marillia.milebag.business.viagem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.viagem.mapper.TrajetoMapper;
import com.marillia.milebag.business.viagem.model.CalculoRota;
import com.marillia.milebag.business.viagem.model.CheckpointGps;
import com.marillia.milebag.business.viagem.model.IniciarViagemRequest;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrajetoIniciadoResponse;
import com.marillia.milebag.business.viagem.model.TrajetoStatus;
import com.marillia.milebag.business.viagem.repository.CalculoRotaRepository;
import com.marillia.milebag.business.viagem.repository.CheckpointGpsRepository;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RotaNaoSelecionadaException;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.TipoSelecao;

/**
 * Cobre o refactor de acoplamento em ViagemService: le a rota selecionada
 * via {@link SelecaoRotaProvider} (nao mais PropostaRotaRepository direto)
 * e escreve o status da entrega via {@link EntregaService#marcarEmAndamento}
 * (nao mais EntregaRepository.save direto) - quebra o ciclo rota<->viagem.
 */
@ExtendWith(MockitoExtension.class)
class ViagemServiceTest {

    @Mock
    private EntregaService entregaService;
    @Mock
    private SelecaoRotaProvider selecaoRotaProvider;
    @Mock
    private TrajetoRepository trajetoRepository;
    @Mock
    private CalculoRotaRepository calculoRotaRepository;
    @Mock
    private CheckpointGpsRepository checkpointGpsRepository;
    @Mock
    private TrajetoMapper trajetoMapper;

    private ViagemService viagemService;

    @BeforeEach
    void setUp() {
        viagemService = new ViagemService(
                entregaService, selecaoRotaProvider, trajetoRepository, calculoRotaRepository,
                checkpointGpsRepository, trajetoMapper, new ObjectMapper());
        // lenient: nem todo teste chega a exercitar os tres saves (ex.: os
        // testes de erro em iniciarViagem retornam antes deles).
        org.mockito.Mockito.lenient().when(trajetoRepository.save(any(Trajeto.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(calculoRotaRepository.save(any(CalculoRota.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(checkpointGpsRepository.save(any(CheckpointGps.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void iniciarViagemLeRotaSelecionadaEMarcaEntregaEmAndamento() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(trajetoRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
        SelecaoRotaProvider.RotaSelecionada selecionada = new SelecaoRotaProvider.RotaSelecionada(
                TipoSelecao.SUGERIDA, new RotaAlternativa("principal", 5000.0, 600, false));
        when(selecaoRotaProvider.buscarSelecionada(entregaId)).thenReturn(Optional.of(selecionada));
        when(trajetoMapper.toIniciadoResponse(any(), any(), any()))
                .thenReturn(new TrajetoIniciadoResponse(UUID.randomUUID(), TrajetoStatus.EM_EXECUCAO, Instant.now(), null, null));

        viagemService.iniciarViagem(entregaId, new IniciarViagemRequest(-23.5, -46.6));

        verify(entregaService).marcarEmAndamento(entregaId);
        verify(trajetoRepository).save(any(Trajeto.class));
        verify(calculoRotaRepository).save(any(CalculoRota.class));
    }

    @Test
    void iniciarViagemLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> viagemService.iniciarViagem(entregaId, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(trajetoRepository, never()).save(any());
        verify(entregaService, never()).marcarEmAndamento(any());
    }

    @Test
    void iniciarViagemLancaConflitoQuandoTrajetoJaExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(trajetoRepository.findByEntregaId(entregaId)).thenReturn(Optional.of(new Trajeto(entregaId, Instant.now())));

        assertThatThrownBy(() -> viagemService.iniciarViagem(entregaId, null))
                .isInstanceOf(ConflitoException.class);
        verify(selecaoRotaProvider, never()).buscarSelecionada(any());
    }

    @Test
    void iniciarViagemLancaQuandoRotaNaoFoiSelecionada() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(trajetoRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
        when(selecaoRotaProvider.buscarSelecionada(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> viagemService.iniciarViagem(entregaId, null))
                .isInstanceOf(RotaNaoSelecionadaException.class);
        verify(entregaService, never()).marcarEmAndamento(any());
        verify(trajetoRepository, never()).save(any());
    }

    @Test
    void buscarTrajetoPorEntregaDelegaParaRepository() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, Instant.now());
        when(trajetoRepository.findByEntregaId(entregaId)).thenReturn(Optional.of(trajeto));

        assertThat(viagemService.buscarTrajetoPorEntrega(entregaId)).contains(trajeto);
    }

    @Test
    void registrarCalculoRotaSeTrajetoExistirRetornaFalseSemTrajeto() {
        UUID entregaId = UUID.randomUUID();
        when(trajetoRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());

        boolean resultado = viagemService.registrarCalculoRotaSeTrajetoExistir(
                entregaId, new RotaAlternativa("x", 100, 10, false), TipoSelecao.SUGERIDA);

        assertThat(resultado).isFalse();
        verify(calculoRotaRepository, never()).save(any());
    }

    @Test
    void registrarCalculoRotaSeTrajetoExistirLancaQuandoJaEncerrado() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, Instant.now());
        trajeto.consolidar(java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, "[]", 10L, null);
        when(trajetoRepository.findByEntregaId(entregaId)).thenReturn(Optional.of(trajeto));

        assertThatThrownBy(() -> viagemService.registrarCalculoRotaSeTrajetoExistir(
                entregaId, new RotaAlternativa("x", 100, 10, false), TipoSelecao.SUGERIDA))
                .isInstanceOf(ConflitoException.class);
    }
}
