package com.marillia.milebag.business.notificacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.marillia.milebag.business.bdo.model.BdoRecebidoPorEmail;
import com.marillia.milebag.business.notificacao.model.InscricaoPush;

@ExtendWith(MockitoExtension.class)
class NotificadorBdoNovoTest {

    @Mock
    private InscricaoPushService inscricaoPushService;
    @Mock
    private EnviadorPush enviadorPush;

    private NotificadorBdoNovo notificador;
    private final BdoRecebidoPorEmail evento = new BdoRecebidoPorEmail(UUID.randomUUID(), "GYNLA12342");

    @BeforeEach
    void setUp() {
        notificador = new NotificadorBdoNovo(inscricaoPushService, enviadorPush, new ObjectMapper());
    }

    @Test
    void enviaATodosOsAparelhosDosMotoristasAtivos() throws Exception {
        InscricaoPush celular = inscricao();
        InscricaoPush tablet = inscricao();
        when(enviadorPush.habilitado()).thenReturn(true);
        when(inscricaoPushService.listarDosMotoristasAtivos()).thenReturn(List.of(celular, tablet));
        when(enviadorPush.enviar(any(), anyString())).thenReturn(201);

        notificador.aoReceberBdoPorEmail(evento);

        verify(enviadorPush).enviar(eq(celular), anyString());
        verify(enviadorPush).enviar(eq(tablet), anyString());
        verify(inscricaoPushService, never()).removerExpirada(any());
    }

    /** O servico de push diz que a inscricao nao existe mais (o usuario limpou os dados do navegador, por exemplo). */
    @Test
    void inscricaoExpiradaERemovida() throws Exception {
        InscricaoPush expirada = inscricao();
        when(enviadorPush.habilitado()).thenReturn(true);
        when(inscricaoPushService.listarDosMotoristasAtivos()).thenReturn(List.of(expirada));
        when(enviadorPush.enviar(any(), anyString())).thenReturn(410);

        notificador.aoReceberBdoPorEmail(evento);

        verify(inscricaoPushService).removerExpirada(expirada.getId());
    }

    @Test
    void falhaNumAparelhoNaoImpedeOsOutros() throws Exception {
        InscricaoPush comFalha = inscricao();
        InscricaoPush boa = inscricao();
        when(enviadorPush.habilitado()).thenReturn(true);
        when(inscricaoPushService.listarDosMotoristasAtivos()).thenReturn(List.of(comFalha, boa));
        when(enviadorPush.enviar(eq(comFalha), anyString())).thenThrow(new java.io.IOException("rede"));
        when(enviadorPush.enviar(eq(boa), anyString())).thenReturn(201);

        notificador.aoReceberBdoPorEmail(evento);

        verify(enviadorPush).enviar(eq(boa), anyString());
    }

    @Test
    void semChavesVapidNaoEnviaNada() {
        when(enviadorPush.habilitado()).thenReturn(false);

        notificador.aoReceberBdoPorEmail(evento);

        verifyNoInteractions(inscricaoPushService);
    }

    @Test
    void conteudoLevaOBdoEAbreOPainel() {
        String conteudo = notificador.conteudo(evento);

        assertThat(conteudo).contains("\"titulo\":\"Nova bagagem para retirar no aeroporto\"");
        assertThat(conteudo).contains("BDO GYNLA12342 chegou por e-mail");
        assertThat(conteudo).contains("\"url\":\"/motorista\"");
    }

    /** Com id: a entidade se compara pelo id, e duas sem id seriam "iguais" para o Mockito. */
    private static InscricaoPush inscricao() {
        InscricaoPush inscricao = new InscricaoPush(UUID.randomUUID(), "https://push.exemplo/" + UUID.randomUUID(), "p256dh", "auth");
        inscricao.setId(UUID.randomUUID());
        return inscricao;
    }
}
