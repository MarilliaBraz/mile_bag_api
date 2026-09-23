package com.marillia.milebag.business.fechamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.fechamento.model.Fechamento;
import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.business.fechamento.model.StatusItemFechamento;
import com.marillia.milebag.business.fechamento.repository.FechamentoRepository;
import com.marillia.milebag.business.fechamento.repository.ItemFechamentoRepository;
import com.marillia.milebag.business.tarifa.model.EscopoTarifa;
import com.marillia.milebag.business.tarifa.model.TabelaTarifa;
import com.marillia.milebag.business.tarifa.service.TabelaTarifaService;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.service.ViagemService;

/**
 * Cobre o refactor de acoplamento em FechamentoService: busca comprovantes
 * via ComprovanteEntregaService, entregas via EntregaService e o trajeto
 * consolidado via ViagemService - nenhum dos tres era injetado via
 * Repository de outro dominio antes do refactor.
 */
@ExtendWith(MockitoExtension.class)
class FechamentoServiceTest {

    @Mock
    private FechamentoRepository fechamentoRepository;
    @Mock
    private ItemFechamentoRepository itemFechamentoRepository;
    @Mock
    private ComprovanteEntregaService comprovanteEntregaService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private ViagemService viagemService;
    @Mock
    private TabelaTarifaService tabelaTarifaService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;

    private FechamentoService fechamentoService;

    @BeforeEach
    void setUp() {
        fechamentoService = new FechamentoService(
                fechamentoRepository, itemFechamentoRepository, comprovanteEntregaService, entregaService,
                viagemService, tabelaTarifaService, bagagemCaronaService, new ObjectMapper());
        // lenient: nem todo teste chega a exercitar todos os 4 stubs (ex.:
        // gerarIgnoraEntregaAindaNaoEncerradaComSucesso nunca salva item).
        org.mockito.Mockito.lenient().when(fechamentoRepository.save(any(Fechamento.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(itemFechamentoRepository.save(any(ItemFechamento.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(bagagemCaronaService.listarPorEntrega(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(fechamentoRepository.findAll()).thenReturn(List.of());
    }

    private Entrega entregaEncerrada(UUID id, UUID motoristaId) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setMotoristaResponsavel(motoristaId);
        entrega.setStatus(EntregaStatus.ENCERRADA_COM_SUCESSO);
        return entrega;
    }

    @Test
    void gerarCalculaItemQuandoTrajetoETarifaDisponiveis() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = LocalDate.of(2026, 1, 15);
        when(fechamentoRepository.findByPeriodoInicioAndPeriodoFim(inicio, fim)).thenReturn(Optional.empty());

        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        ComprovanteEntrega comprovante = new ComprovanteEntrega(
                entregaId, "foto.jpg", "Fulano", Instant.parse("2026-01-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaEncerrada(entregaId, motoristaId)));

        Trajeto trajeto = new Trajeto(entregaId, Instant.now());
        trajeto.consolidar(new BigDecimal("10.000"), new BigDecimal("10.000"), "[]", 600L, null);
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.of(trajeto));

        TabelaTarifa tarifa = new TabelaTarifa(EscopoTarifa.MOTORISTA, motoristaId, null, new BigDecimal("50.00"), new BigDecimal("2.00"), inicio);
        when(tabelaTarifaService.resolverTarifaAplicavel(motoristaId, null)).thenReturn(Optional.of(tarifa));

        fechamentoService.gerar(inicio, fim);

        ArgumentCaptor<ItemFechamento> captor = ArgumentCaptor.forClass(ItemFechamento.class);
        org.mockito.Mockito.verify(itemFechamentoRepository).save(captor.capture());
        ItemFechamento item = captor.getValue();
        assertThat(item.getStatusItem()).isEqualTo(StatusItemFechamento.CALCULADO);
        // 50 fixo + 2 * 20km (10 ida + 10 volta) = 90
        assertThat(item.getValorPagamentoEntrega()).isEqualByComparingTo("90.00");
    }

    @Test
    void gerarMarcaItemPendenteQuandoTrajetoNaoConsolidado() {
        LocalDate inicio = LocalDate.of(2026, 2, 1);
        LocalDate fim = LocalDate.of(2026, 2, 15);
        when(fechamentoRepository.findByPeriodoInicioAndPeriodoFim(inicio, fim)).thenReturn(Optional.empty());

        UUID entregaId = UUID.randomUUID();
        ComprovanteEntrega comprovante = new ComprovanteEntrega(
                entregaId, "foto.jpg", "Fulano", Instant.parse("2026-02-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaEncerrada(entregaId, UUID.randomUUID())));
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.empty());

        fechamentoService.gerar(inicio, fim);

        ArgumentCaptor<ItemFechamento> captor = ArgumentCaptor.forClass(ItemFechamento.class);
        org.mockito.Mockito.verify(itemFechamentoRepository).save(captor.capture());
        assertThat(captor.getValue().getStatusItem()).isEqualTo(StatusItemFechamento.PENDENTE);
        assertThat(captor.getValue().getMotivoPendencia()).contains("trajeto da viagem nao consolidado");
    }

    @Test
    void gerarIgnoraEntregaAindaNaoEncerradaComSucesso() {
        LocalDate inicio = LocalDate.of(2026, 3, 1);
        LocalDate fim = LocalDate.of(2026, 3, 15);
        when(fechamentoRepository.findByPeriodoInicioAndPeriodoFim(inicio, fim)).thenReturn(Optional.empty());

        UUID entregaId = UUID.randomUUID();
        ComprovanteEntrega comprovante = new ComprovanteEntrega(
                entregaId, "foto.jpg", "Fulano", Instant.parse("2026-03-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        Entrega emAndamento = new Entrega();
        emAndamento.setId(entregaId);
        emAndamento.setStatus(EntregaStatus.EM_ANDAMENTO);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(emAndamento));

        fechamentoService.gerar(inicio, fim);

        org.mockito.Mockito.verify(itemFechamentoRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(viagemService, org.mockito.Mockito.never()).buscarTrajetoPorEntrega(any());
    }
}
