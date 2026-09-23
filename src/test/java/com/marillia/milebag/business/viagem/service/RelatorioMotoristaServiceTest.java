package com.marillia.milebag.business.viagem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.business.viagem.model.ItemRelatorioMotoristaResponse;
import com.marillia.milebag.business.viagem.model.RelatorioQuinzenalResponse;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Cobre HU17: relatorio quinzenal sob demanda do motorista autenticado. */
@ExtendWith(MockitoExtension.class)
class RelatorioMotoristaServiceTest {

    @Mock
    private ComprovanteEntregaService comprovanteEntregaService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private ViagemService viagemService;
    @Mock
    private AjusteViagemService ajusteViagemService;
    @Mock
    private BdoService bdoService;

    private RelatorioMotoristaService relatorioMotoristaService;

    @BeforeEach
    void setUp() {
        relatorioMotoristaService =
                new RelatorioMotoristaService(comprovanteEntregaService, entregaService, viagemService, ajusteViagemService, bdoService);
    }

    private Entrega entregaDe(UUID id, UUID motoristaId) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setMotoristaResponsavel(motoristaId);
        return entrega;
    }

    @Test
    void consultarRelatorioRecusaPeriodoAusente() {
        assertThatThrownBy(() -> relatorioMotoristaService.consultarRelatorio(UUID.randomUUID(), null, LocalDate.now()))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void consultarRelatorioRecusaPeriodoFimAntesDoInicio() {
        LocalDate inicio = LocalDate.of(2026, 1, 15);
        LocalDate fim = LocalDate.of(2026, 1, 1);
        assertThatThrownBy(() -> relatorioMotoristaService.consultarRelatorio(UUID.randomUUID(), inicio, fim))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void consultarRelatorioIgnoraEntregaDeOutroMotorista() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = LocalDate.of(2026, 1, 15);
        UUID entregaId = UUID.randomUUID();
        UUID motoristaAutenticado = UUID.randomUUID();
        UUID outroMotorista = UUID.randomUUID();
        ComprovanteEntrega comprovante =
                new ComprovanteEntrega(entregaId, "foto.jpg", "Fulano", Instant.parse("2026-01-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, outroMotorista)));

        RelatorioQuinzenalResponse relatorio = relatorioMotoristaService.consultarRelatorio(motoristaAutenticado, inicio, fim);

        assertThat(relatorio.entregas()).isEmpty();
    }

    @Test
    void consultarRelatorioIgnoraEntregaSemTrajetoConsolidado() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = LocalDate.of(2026, 1, 15);
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        ComprovanteEntrega comprovante =
                new ComprovanteEntrega(entregaId, "foto.jpg", "Fulano", Instant.parse("2026-01-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId)));
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.empty());

        RelatorioQuinzenalResponse relatorio = relatorioMotoristaService.consultarRelatorio(motoristaId, inicio, fim);

        assertThat(relatorio.entregas()).isEmpty();
    }

    @Test
    void consultarRelatorioMontaItemComValoresVigentes() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = LocalDate.of(2026, 1, 15);
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        ComprovanteEntrega comprovante =
                new ComprovanteEntrega(entregaId, "foto.jpg", "Fulano", Instant.parse("2026-01-05T12:00:00Z"), 0, 0, false, null);
        when(comprovanteEntregaService.listarPorPeriodo(any(), any())).thenReturn(List.of(comprovante));
        Entrega entrega = entregaDe(entregaId, motoristaId);
        entrega.setBdoId(bdoId);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));

        Trajeto trajeto = new Trajeto(entregaId, Instant.now());
        trajeto.consolidar(new BigDecimal("10.000"), new BigDecimal("10.000"), "[]", 600L, null);
        when(viagemService.buscarTrajetoPorEntrega(entregaId)).thenReturn(Optional.of(trajeto));

        Bdo bdo = new Bdo();
        bdo.setNumero("BDO-123");
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));

        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.KM_IDA)).thenReturn(Optional.of(new BigDecimal("10.000")));
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.KM_VOLTA)).thenReturn(Optional.of(new BigDecimal("10.000")));
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA)).thenReturn(Optional.of(new BigDecimal("90.00")));
        when(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO)).thenReturn(Optional.of(BigDecimal.ZERO));
        when(ajusteViagemService.temAjuste(trajeto.getId())).thenReturn(true);

        RelatorioQuinzenalResponse relatorio = relatorioMotoristaService.consultarRelatorio(motoristaId, inicio, fim);

        assertThat(relatorio.entregas()).hasSize(1);
        ItemRelatorioMotoristaResponse item = relatorio.entregas().get(0);
        assertThat(item.entregaId()).isEqualTo(entregaId);
        assertThat(item.bdoNumero()).isEqualTo("BDO-123");
        assertThat(item.kmIda()).isEqualByComparingTo("10.000");
        assertThat(item.tarifaAplicada()).isEqualByComparingTo("90.00");
        assertThat(item.temAjuste()).isTrue();
    }
}
