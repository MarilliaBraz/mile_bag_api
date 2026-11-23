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
import com.marillia.milebag.business.dadosbdo.model.DadosBdoAlterados;
import com.marillia.milebag.business.notificacao.model.InscricaoPush;

@ExtendWith(MockitoExtension.class)
class NotificadorDadosBdoTest {

    @Mock
    private InscricaoPushService inscricaoPushService;
    @Mock
    private EnviadorPush enviadorPush;

    private NotificadorDadosBdo notificador;
    private final UUID motorista = UUID.randomUUID();
    private final DadosBdoAlterados endereco = new DadosBdoAlterados(UUID.randomUUID(), "GYNLA12342", motorista, true);

    @BeforeEach
    void setUp() {
        notificador = new NotificadorDadosBdo(inscricaoPushService, new DisparadorPush(inscricaoPushService, enviadorPush), new ObjectMapper());
    }

    @Test
    void avisaSoOsAparelhosDoMotoristaQueEstaComAEntrega() throws Exception {
        InscricaoPush celular = new InscricaoPush(UUID.randomUUID(), "https://push.exemplo/" + UUID.randomUUID(), "p256dh", "auth");
        celular.setId(UUID.randomUUID());
        when(enviadorPush.habilitado()).thenReturn(true);
        when(inscricaoPushService.listarDoUsuario(motorista)).thenReturn(List.of(celular));
        when(enviadorPush.enviar(any(), anyString())).thenReturn(201);

        notificador.aoAlterarDados(endereco);

        verify(enviadorPush).enviar(eq(celular), anyString());
    }

    @Test
    void conteudoDizSeOEnderecoMudou() {
        assertThat(notificador.conteudo(endereco))
                .contains("\"tipo\":\"bdo-dados-alterados\"")
                .contains("A empresa mudou o endereço de entrega do BDO GYNLA12342.");
        assertThat(notificador.conteudo(new DadosBdoAlterados(UUID.randomUUID(), null, motorista, false)))
                .contains("A empresa corrigiu os dados do passageiro de uma entrega sua.");
    }

    @Test
    void falhaAoListarOsAparelhosNaoPropaga() {
        when(enviadorPush.habilitado()).thenReturn(true);
        when(inscricaoPushService.listarDoUsuario(motorista)).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(() -> notificador.aoAlterarDados(endereco)).doesNotThrowAnyException();
    }

    @Test
    void semChavesVapidNaoEnviaNada() {
        when(enviadorPush.habilitado()).thenReturn(false);

        notificador.aoAlterarDados(endereco);

        verifyNoInteractions(inscricaoPushService);
    }
}
