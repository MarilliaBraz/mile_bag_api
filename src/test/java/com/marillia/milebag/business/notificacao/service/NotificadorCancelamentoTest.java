package com.marillia.milebag.business.notificacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.cancelamento.model.BdoCancelado;
import com.marillia.milebag.business.notificacao.model.InscricaoPush;

@ExtendWith(MockitoExtension.class)
class NotificadorCancelamentoTest {

    @Mock
    private InscricaoPushService inscricaoPushService;
    @Mock
    private EnviadorPush enviadorPush;

    private NotificadorCancelamento notificador;
    private final UUID motorista = UUID.randomUUID();
    private final BdoCancelado evento = new BdoCancelado(UUID.randomUUID(), "GYNLA12342", motorista, "Enviado por engano");

    @BeforeEach
    void setUp() {
        notificador = new NotificadorCancelamento(
                inscricaoPushService, new DisparadorPush(inscricaoPushService, enviadorPush), new ObjectMapper());
    }

    @Test
    void avisaSoOsAparelhosDoMotoristaQueEstavaComAEntrega() throws Exception {
        InscricaoPush celular = inscricao();
        when(enviadorPush.habilitado()).thenReturn(true);
        when(inscricaoPushService.listarDoUsuario(motorista)).thenReturn(List.of(celular));
        when(enviadorPush.enviar(any(), anyString())).thenReturn(201);

        notificador.aoCancelarBdo(evento);

        verify(enviadorPush).enviar(eq(celular), anyString());
    }

    @Test
    void conteudoLevaOTipoOMotivoEAbreOPainel() {
        String conteudo = notificador.conteudo(evento);

        assertThat(conteudo).contains("\"tipo\":\"bdo-cancelado\"");
        assertThat(conteudo).contains("\"titulo\":\"Entrega cancelada pela empresa\"");
        assertThat(conteudo).contains("A empresa cancelou a entrega do BDO GYNLA12342. Motivo: Enviado por engano.");
        assertThat(conteudo).contains("\"url\":\"/motorista\"");
    }

    /** Depois do commit: uma falha aqui nao pode virar erro para a empresa, que ja cancelou. */
    @Test
    void falhaAoListarOsAparelhosNaoPropaga() {
        when(enviadorPush.habilitado()).thenReturn(true);
        when(inscricaoPushService.listarDoUsuario(motorista)).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(() -> notificador.aoCancelarBdo(evento)).doesNotThrowAnyException();
    }

    @Test
    void semChavesVapidNaoEnviaNada() {
        when(enviadorPush.habilitado()).thenReturn(false);

        notificador.aoCancelarBdo(evento);

        verifyNoInteractions(inscricaoPushService);
    }

    private static InscricaoPush inscricao() {
        InscricaoPush inscricao = new InscricaoPush(UUID.randomUUID(), "https://push.exemplo/" + UUID.randomUUID(), "p256dh", "auth");
        inscricao.setId(UUID.randomUUID());
        return inscricao;
    }
}
