package com.marillia.milebag.business.emergencia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.emergencia.model.AlertaEmergencia;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

/** Cobre HU30: o e-mail aos administradores e que uma falha de envio nao derruba o alerta. */
@ExtendWith(MockitoExtension.class)
class NotificadorEmergenciaTest {

    @Mock
    private EmailSender emailSender;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;

    private NotificadorEmergencia notificador;

    private final AlertaEmergencia comPosicao =
            new AlertaEmergencia(UUID.randomUUID(), null, -16.686891, -49.264794, Instant.parse("2026-10-22T23:30:00Z"));

    @BeforeEach
    void setUp() {
        notificador = new NotificadorEmergencia(emailSender, usuarioConsultaService, "America/Sao_Paulo");
    }

    @Test
    void corpoTrazHoraNoFusoDoTenantELinkDoMapa() {
        String corpo = notificador.corpo(comPosicao, "Joao");

        assertThat(corpo).contains("Joao").contains("22/10/2026 as 20:30");
        assertThat(corpo).contains("https://www.openstreetmap.org/?mlat=-16.686891&mlon=-49.264794");
        assertThat(corpo).contains("190");
    }

    @Test
    void semPosicaoOCorpoAvisaQueOGpsNaoRespondeu() {
        AlertaEmergencia semPosicao = new AlertaEmergencia(UUID.randomUUID(), null, null, null, Instant.now());

        assertThat(notificador.corpo(semPosicao, "Joao")).contains("nao informou a posicao").doesNotContain("openstreetmap");
    }

    @Test
    void avisaCadaAdministradorEUmaFalhaNaoImpedeOsDemais() throws Exception {
        when(usuarioConsultaService.emailsDosAdministradoresAtivos()).thenReturn(List.of("a@empresa.com", "b@empresa.com"));
        doThrow(new MessagingException("smtp fora")).when(emailSender).enviar(eq("a@empresa.com"), anyString(), anyString());

        notificador.notificarAdministradores(comPosicao, "Joao");

        verify(emailSender).enviar(eq("b@empresa.com"), anyString(), anyString());
    }

    @Test
    void semAdministradorComEmailNaoEnviaNada() throws Exception {
        when(usuarioConsultaService.emailsDosAdministradoresAtivos()).thenReturn(List.of());

        notificador.notificarAdministradores(comPosicao, "Joao");

        verify(emailSender, never()).enviar(any(), any(), any());
    }
}
