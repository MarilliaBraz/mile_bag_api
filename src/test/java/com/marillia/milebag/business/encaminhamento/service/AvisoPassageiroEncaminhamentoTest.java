package com.marillia.milebag.business.encaminhamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.encaminhamento.model.BagagemEncaminhada;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

@ExtendWith(MockitoExtension.class)
class AvisoPassageiroEncaminhamentoTest {

    @Mock
    private EmailSender emailSender;

    private final BagagemEncaminhada evento = new BagagemEncaminhada(
            UUID.randomUUID(), "maria@exemplo.com", "SILVA/MARIA", "Linhas Aéreas Exemplo (ZZ)", "Entregas Rápidas", "0957123456");

    @Test
    void avisaOPassageiroComACompanhiaAEmpresaEAEtiqueta() throws Exception {
        new AvisoPassageiroEncaminhamento(emailSender).aoEncaminhar(evento);

        verify(emailSender).enviar(eq("maria@exemplo.com"), eq("Sua bagagem foi encontrada - Linhas Aéreas Exemplo (ZZ)"), anyString());
        assertThat(AvisoPassageiroEncaminhamento.corpo(evento))
                .contains("Olá, SILVA/MARIA.", "encontrou a sua bagagem (0957123456)", "pela Entregas Rápidas", "acompanhar a bagagem em tempo real");
    }

    @Test
    void falhaDoEmailNaoLanca() throws Exception {
        doThrow(new MessagingException("smtp fora")).when(emailSender).enviar(anyString(), anyString(), anyString());

        assertThatCode(() -> new AvisoPassageiroEncaminhamento(emailSender).aoEncaminhar(evento)).doesNotThrowAnyException();
    }
}
