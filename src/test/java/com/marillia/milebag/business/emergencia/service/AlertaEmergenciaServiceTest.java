package com.marillia.milebag.business.emergencia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.emergencia.model.AlertaEmergencia;
import com.marillia.milebag.business.emergencia.model.StatusAlertaEmergencia;
import com.marillia.milebag.business.emergencia.repository.AlertaEmergenciaRepository;
import com.marillia.milebag.business.entrega.model.EntregaResponse;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Cobre HU30 (RF23/RD16): acionar, idempotencia por motorista, cancelar e atender. */
@ExtendWith(MockitoExtension.class)
class AlertaEmergenciaServiceTest {

    @Mock
    private AlertaEmergenciaRepository alertaRepository;
    @Mock
    private EntregaService entregaService;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;
    @Mock
    private NotificadorEmergencia notificador;

    @InjectMocks
    private AlertaEmergenciaService service;

    private final UUID motoristaId = UUID.randomUUID();

    @Test
    void acionarRegistraPosicaoEntregaEmAndamentoEAvisaOsAdministradores() {
        UUID entregaId = UUID.randomUUID();
        when(alertaRepository.findByMotoristaIdAndStatus(motoristaId, StatusAlertaEmergencia.ABERTO)).thenReturn(Optional.empty());
        EntregaResponse emAndamento = new EntregaResponse(entregaId, UUID.randomUUID(), EntregaStatus.EM_ANDAMENTO, motoristaId, null);
        when(entregaService.listarPorMotorista(motoristaId, EntregaStatus.EM_ANDAMENTO)).thenReturn(List.of(emAndamento));
        when(alertaRepository.saveAndFlush(any(AlertaEmergencia.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioConsultaService.nomeParaExibicao(motoristaId)).thenReturn(Optional.of("Joao"));

        var resposta = service.acionar(motoristaId, -16.68, -49.25);

        assertThat(resposta.novo()).isTrue();
        assertThat(resposta.status()).isEqualTo(StatusAlertaEmergencia.ABERTO);
        assertThat(resposta.entregaId()).isEqualTo(entregaId);
        assertThat(resposta.latitude()).isEqualTo(-16.68);
        verify(alertaRepository).travarPorMotorista(motoristaId);
        verify(notificador).notificarAdministradores(any(AlertaEmergencia.class), eq("Joao"));
    }

    @Test
    void acionarSemPosicaoTambemRegistra() {
        when(alertaRepository.findByMotoristaIdAndStatus(motoristaId, StatusAlertaEmergencia.ABERTO)).thenReturn(Optional.empty());
        when(entregaService.listarPorMotorista(motoristaId, EntregaStatus.EM_ANDAMENTO)).thenReturn(List.of());
        when(alertaRepository.saveAndFlush(any(AlertaEmergencia.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioConsultaService.nomeParaExibicao(motoristaId)).thenReturn(Optional.empty());

        var resposta = service.acionar(motoristaId, null, null);

        assertThat(resposta.novo()).isTrue();
        assertThat(resposta.latitude()).isNull();
        assertThat(resposta.motoristaNome()).isEqualTo("Motorista");
    }

    @Test
    void acionarComAlertaJaAbertoDevolveOMesmoSemAvisarDeNovo() {
        AlertaEmergencia aberto = new AlertaEmergencia(motoristaId, null, null, null, Instant.now());
        when(alertaRepository.findByMotoristaIdAndStatus(motoristaId, StatusAlertaEmergencia.ABERTO)).thenReturn(Optional.of(aberto));

        var resposta = service.acionar(motoristaId, null, null);

        assertThat(resposta.novo()).isFalse();
        verify(alertaRepository, never()).saveAndFlush(any());
        verify(notificador, never()).notificarAdministradores(any(), any());
    }

    @Test
    void coordenadaIncompletaOuForaDosLimitesERecusada() {
        assertThatThrownBy(() -> service.acionar(motoristaId, -16.68, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.acionar(motoristaId, 91.0, 0.0)).isInstanceOf(RegraNegocioException.class);
        verify(alertaRepository, never()).saveAndFlush(any());
    }

    @Test
    void motoristaSoCancelaOProprioAlerta() {
        UUID alertaId = UUID.randomUUID();
        AlertaEmergencia deOutro = new AlertaEmergencia(UUID.randomUUID(), null, null, null, Instant.now());
        when(alertaRepository.findById(alertaId)).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> service.cancelar(alertaId, motoristaId)).isInstanceOf(AcessoNegadoException.class);
        assertThat(deOutro.getStatus()).isEqualTo(StatusAlertaEmergencia.ABERTO);
    }

    @Test
    void atenderRegistraObservacaoEAutoriaESoUmaVez() {
        UUID alertaId = UUID.randomUUID();
        UUID administradorId = UUID.randomUUID();
        AlertaEmergencia alerta = new AlertaEmergencia(motoristaId, null, null, null, Instant.now());
        when(alertaRepository.findById(alertaId)).thenReturn(Optional.of(alerta));
        when(alertaRepository.save(alerta)).thenReturn(alerta);

        var resposta = service.atender(alertaId, administradorId, "  Liguei, estava tudo bem  ");

        assertThat(resposta.status()).isEqualTo(StatusAlertaEmergencia.ATENDIDO);
        assertThat(resposta.observacao()).isEqualTo("Liguei, estava tudo bem");
        assertThat(alerta.getEncerradoPor()).isEqualTo(administradorId);
        assertThatThrownBy(() -> service.atender(alertaId, administradorId, null)).isInstanceOf(ConflitoException.class);
    }
}
