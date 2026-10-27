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

import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.fechamento.model.Fechamento;
import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.business.fechamento.model.StatusItemFechamento;
import com.marillia.milebag.business.fechamento.repository.FechamentoRepository;
import com.marillia.milebag.business.fechamento.repository.ItemFechamentoRepository;
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.service.AjusteViagemService;
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
    private AjusteViagemService ajusteViagemService;

    private FechamentoService fechamentoService;

    @BeforeEach
    void setUp() {
        fechamentoService = new FechamentoService(
                fechamentoRepository, itemFechamentoRepository, comprovanteEntregaService, entregaService, viagemService, ajusteViagemService);
        // lenient: nem todo teste chega a exercitar todos os stubs (ex.:
        // gerarIgnoraEntregaAindaNaoEncerradaComSucesso nunca salva item).
        org.mockito.Mockito.lenient().when(fechamentoRepository.save(any(Fechamento.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(itemFechamentoRepository.save(any(ItemFechamento.class))).thenAnswer(inv -> inv.getArgument(0));
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

        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        trajeto.consolidar(new BigDecimal("10.000"), new BigDecimal("10.000"), "[]", 600L, null);
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.of(trajeto));

        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA))
                .thenReturn(Optional.of(new BigDecimal("90.00")));
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        fechamentoService.gerar(inicio, fim);

        ArgumentCaptor<ItemFechamento> captor = ArgumentCaptor.forClass(ItemFechamento.class);
        org.mockito.Mockito.verify(itemFechamentoRepository).save(captor.capture());
        ItemFechamento item = captor.getValue();
        assertThat(item.getStatusItem()).isEqualTo(StatusItemFechamento.CALCULADO);
        assertThat(item.getValorPagamentoEntrega()).isEqualByComparingTo("90.00");
    }

    /** HU07 (decisao de 2026-10-24): a viagem da tentativa sem sucesso e paga a quem dirigiu; sem motorista registrado, fica de fora. */
    @Test
    void gerarPagaAViagemDaTentativaSemSucessoAQuemDirigiu() {
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fim = LocalDate.of(2026, 5, 15);
        when(fechamentoRepository.findByPeriodoInicioAndPeriodoFim(inicio, fim)).thenReturn(Optional.empty());
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of());

        UUID quemDirigiu = UUID.randomUUID();
        Trajeto tentativa = new Trajeto(UUID.randomUUID(), quemDirigiu, Instant.parse("2026-05-03T10:00:00Z"));
        tentativa.consolidar(new BigDecimal("8.000"), new BigDecimal("8.000"), "[]", 900L, null);
        tentativa.vincularOcorrencia(UUID.randomUUID(), Instant.parse("2026-05-03T11:00:00Z"));
        Trajeto semMotorista = new Trajeto(UUID.randomUUID(), null, Instant.parse("2026-05-04T10:00:00Z"));
        semMotorista.vincularOcorrencia(UUID.randomUUID(), Instant.parse("2026-05-04T11:00:00Z"));
        when(viagemService.listarEncerradosPorOcorrencia(any(), any())).thenReturn(List.of(tentativa, semMotorista));
        when(ajusteViagemService.resolverValorVigente(tentativa.getId(), CampoAjusteViagem.TARIFA_APLICADA))
                .thenReturn(Optional.of(new BigDecimal("72.00")));
        when(ajusteViagemService.resolverValorVigente(tentativa.getId(), CampoAjusteViagem.VALOR_PEDAGIO))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        fechamentoService.gerar(inicio, fim);

        ArgumentCaptor<ItemFechamento> captor = ArgumentCaptor.forClass(ItemFechamento.class);
        org.mockito.Mockito.verify(itemFechamentoRepository).save(captor.capture());
        ItemFechamento item = captor.getValue();
        assertThat(item.isTentativaSemSucesso()).isTrue();
        assertThat(item.getMotoristaId()).isEqualTo(quemDirigiu);
        assertThat(item.getTrajetoId()).isEqualTo(tentativa.getId());
        assertThat(item.getValorPagamentoEntrega()).isEqualByComparingTo("72.00");
    }

    /** HU17 FR-006: um AjusteViagem mais recente que o valor original passa a ser o valor usado no fechamento. */
    @Test
    void gerarUsaValorAjustadoPeloMotoristaQuandoExiste() {
        LocalDate inicio = LocalDate.of(2026, 4, 1);
        LocalDate fim = LocalDate.of(2026, 4, 15);
        when(fechamentoRepository.findByPeriodoInicioAndPeriodoFim(inicio, fim)).thenReturn(Optional.empty());

        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        ComprovanteEntrega comprovante = new ComprovanteEntrega(
                entregaId, "foto.jpg", "Fulano", Instant.parse("2026-04-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaEncerrada(entregaId, motoristaId)));

        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        trajeto.consolidar(new BigDecimal("10.000"), new BigDecimal("10.000"), "[]", 600L, null);
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.of(trajeto));

        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA))
                .thenReturn(Optional.of(new BigDecimal("150.00")));
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        fechamentoService.gerar(inicio, fim);

        ArgumentCaptor<ItemFechamento> captor = ArgumentCaptor.forClass(ItemFechamento.class);
        org.mockito.Mockito.verify(itemFechamentoRepository).save(captor.capture());
        assertThat(captor.getValue().getValorPagamentoEntrega()).isEqualByComparingTo("150.00");
    }

    @Test
    void gerarMarcaItemPendenteQuandoTarifaNaoResolvida() {
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fim = LocalDate.of(2026, 5, 15);
        when(fechamentoRepository.findByPeriodoInicioAndPeriodoFim(inicio, fim)).thenReturn(Optional.empty());

        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        ComprovanteEntrega comprovante = new ComprovanteEntrega(
                entregaId, "foto.jpg", "Fulano", Instant.parse("2026-05-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaEncerrada(entregaId, motoristaId)));

        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        trajeto.consolidar(new BigDecimal("10.000"), new BigDecimal("10.000"), "[]", 600L, null);
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.of(trajeto));

        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA)).thenReturn(Optional.empty());
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        fechamentoService.gerar(inicio, fim);

        ArgumentCaptor<ItemFechamento> captor = ArgumentCaptor.forClass(ItemFechamento.class);
        org.mockito.Mockito.verify(itemFechamentoRepository).save(captor.capture());
        assertThat(captor.getValue().getStatusItem()).isEqualTo(StatusItemFechamento.PENDENTE);
        assertThat(captor.getValue().getMotivoPendencia()).contains("nenhuma tarifa aplicavel configurada");
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

    /** Valores em dinheiro saem com 2 casas ja na resposta do POST (antes vinham como 233,216 e so o banco arredondava). */
    @Test
    void gerarArredondaOsValoresDoItemParaCentavos() {
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fim = LocalDate.of(2026, 5, 15);
        when(fechamentoRepository.findByPeriodoInicioAndPeriodoFim(inicio, fim)).thenReturn(Optional.empty());
        UUID entregaId = UUID.randomUUID();
        ComprovanteEntrega comprovante = new ComprovanteEntrega(
                entregaId, "foto.jpg", "Fulano", Instant.parse("2026-05-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaEncerrada(entregaId, UUID.randomUUID())));
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        trajeto.consolidar(new BigDecimal("58.300"), new BigDecimal("58.300"), "[]", 600L, null);
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.of(trajeto));
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA)).thenReturn(Optional.of(new BigDecimal("233.216")));
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO)).thenReturn(Optional.of(new BigDecimal("17.005")));

        fechamentoService.gerar(inicio, fim);

        ArgumentCaptor<ItemFechamento> captor = ArgumentCaptor.forClass(ItemFechamento.class);
        org.mockito.Mockito.verify(itemFechamentoRepository).save(captor.capture());
        assertThat(captor.getValue().getValorPagamentoEntrega()).isEqualTo(new BigDecimal("233.22"));
        assertThat(captor.getValue().getValorReembolsoPedagio()).isEqualTo(new BigDecimal("17.01"));
    }
}
