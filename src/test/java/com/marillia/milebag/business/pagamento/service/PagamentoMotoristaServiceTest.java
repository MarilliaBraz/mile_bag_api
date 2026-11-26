package com.marillia.milebag.business.pagamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.business.fechamento.model.StatusItemFechamento;
import com.marillia.milebag.business.fechamento.service.FechamentoService;
import com.marillia.milebag.business.pagamento.model.EventoPagamentoMotorista;
import com.marillia.milebag.business.pagamento.model.PagamentoMotorista;
import com.marillia.milebag.business.pagamento.model.SituacaoPagamento;
import com.marillia.milebag.business.pagamento.model.TipoEventoPagamento;
import com.marillia.milebag.business.pagamento.repository.EventoPagamentoMotoristaRepository;
import com.marillia.milebag.business.pagamento.repository.PagamentoMotoristaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** V60: a empresa agenda e marca como pago a quinzena de cada motorista; desfazer o pago exige motivo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagamentoMotoristaServiceTest {

    @Mock
    private PagamentoMotoristaRepository pagamentoRepository;
    @Mock
    private EventoPagamentoMotoristaRepository eventoRepository;
    @Mock
    private FechamentoService fechamentoService;

    private PagamentoMotoristaService servico;
    private final UUID fechamentoId = UUID.randomUUID();
    private final UUID motoristaId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now(PagamentoMotoristaService.BRASILIA);

    @BeforeEach
    void setUp() {
        servico = new PagamentoMotoristaService(pagamentoRepository, eventoRepository, fechamentoService);
        when(fechamentoService.itensCalculados(fechamentoId)).thenReturn(List.of(item(motoristaId, UUID.randomUUID())));
        when(pagamentoRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    private ItemFechamento item(UUID motorista, UUID entregaId) {
        return new ItemFechamento(fechamentoId, entregaId, motorista, new BigDecimal("80.00"), new BigDecimal("12.50"),
                StatusItemFechamento.CALCULADO, null);
    }

    @Test
    void agendarCriaOPagamentoERegistraQuemEQuando() {
        servico.agendar(fechamentoId, motoristaId, hoje.plusDays(3), adminId);

        ArgumentCaptor<PagamentoMotorista> pagamento = ArgumentCaptor.forClass(PagamentoMotorista.class);
        verify(pagamentoRepository).save(pagamento.capture());
        assertThat(pagamento.getValue().getSituacao()).isEqualTo(SituacaoPagamento.AGENDADO);
        assertThat(pagamento.getValue().getDataAgendada()).isEqualTo(hoje.plusDays(3));
        ArgumentCaptor<EventoPagamentoMotorista> evento = ArgumentCaptor.forClass(EventoPagamentoMotorista.class);
        verify(eventoRepository).save(evento.capture());
        assertThat(evento.getValue().getTipo()).isEqualTo(TipoEventoPagamento.AGENDADO);
        assertThat(evento.getValue().getUsuarioId()).isEqualTo(adminId);
    }

    @Test
    void agendarRecusaDataNoPassadoEMotoristaSemEntregaCalculada() {
        assertThatThrownBy(() -> servico.agendar(fechamentoId, motoristaId, hoje.minusDays(1), adminId))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> servico.agendar(fechamentoId, UUID.randomUUID(), hoje, adminId))
                .isInstanceOf(RegraNegocioException.class);
        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    void marcarPagoSemDataValeHojeENaoPagaDuasVezes() {
        servico.marcarPago(fechamentoId, motoristaId, null, adminId);

        ArgumentCaptor<PagamentoMotorista> pagamento = ArgumentCaptor.forClass(PagamentoMotorista.class);
        verify(pagamentoRepository).save(pagamento.capture());
        assertThat(pagamento.getValue().getSituacao()).isEqualTo(SituacaoPagamento.PAGO);
        assertThat(pagamento.getValue().getPagoEm()).isEqualTo(hoje);

        when(pagamentoRepository.findByFechamentoIdAndMotoristaId(fechamentoId, motoristaId)).thenReturn(Optional.of(pagamento.getValue()));
        assertThatThrownBy(() -> servico.marcarPago(fechamentoId, motoristaId, null, adminId)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void desfazerPagoExigeMotivoEVoltaParaAgendado() {
        PagamentoMotorista pago = new PagamentoMotorista(fechamentoId, motoristaId, adminId);
        pago.agendar(hoje, adminId);
        pago.marcarPago(hoje, adminId);
        when(pagamentoRepository.findByFechamentoIdAndMotoristaId(fechamentoId, motoristaId)).thenReturn(Optional.of(pago));

        assertThatThrownBy(() -> servico.desfazerPago(fechamentoId, motoristaId, "  ", adminId)).isInstanceOf(RegraNegocioException.class);

        servico.desfazerPago(fechamentoId, motoristaId, "Marquei o motorista errado", adminId);

        assertThat(pago.getSituacao()).isEqualTo(SituacaoPagamento.AGENDADO);
        assertThat(pago.getPagoEm()).isNull();
        ArgumentCaptor<EventoPagamentoMotorista> evento = ArgumentCaptor.forClass(EventoPagamentoMotorista.class);
        verify(eventoRepository).save(evento.capture());
        assertThat(evento.getValue().getTipo()).isEqualTo(TipoEventoPagamento.PAGAMENTO_DESFEITO);
        assertThat(evento.getValue().getMotivo()).isEqualTo("Marquei o motorista errado");
    }

    @Test
    void pagasEntreTrazSoAsEntregasDoMotoristaJaPago() {
        UUID outroMotorista = UUID.randomUUID();
        UUID entregaPaga = UUID.randomUUID();
        UUID entregaNaoPaga = UUID.randomUUID();
        when(fechamentoService.itensCalculadosDasEntregas(any()))
                .thenReturn(List.of(item(motoristaId, entregaPaga), item(outroMotorista, entregaNaoPaga)));
        PagamentoMotorista pago = new PagamentoMotorista(fechamentoId, motoristaId, adminId);
        pago.marcarPago(hoje, adminId);
        when(pagamentoRepository.findBySituacao(SituacaoPagamento.PAGO)).thenReturn(List.of(pago));

        assertThat(servico.pagasEntre(List.of(entregaPaga, entregaNaoPaga))).containsExactlyEntriesOf(java.util.Map.of(entregaPaga, hoje));
    }
}
