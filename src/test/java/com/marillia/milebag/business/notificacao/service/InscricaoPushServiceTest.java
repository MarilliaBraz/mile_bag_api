package com.marillia.milebag.business.notificacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.notificacao.model.InscreverPushRequest;
import com.marillia.milebag.business.notificacao.model.InscricaoPush;
import com.marillia.milebag.business.notificacao.repository.InscricaoPushRepository;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class InscricaoPushServiceTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

    @Mock
    private InscricaoPushRepository repository;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;
    @InjectMocks
    private InscricaoPushService service;

    @Test
    void inscreveUmAparelhoNovo() {
        UUID motorista = UUID.randomUUID();
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());

        service.inscrever(motorista, pedido());

        ArgumentCaptor<InscricaoPush> salva = ArgumentCaptor.forClass(InscricaoPush.class);
        verify(repository).save(salva.capture());
        assertThat(salva.getValue().getUsuarioId()).isEqualTo(motorista);
        assertThat(salva.getValue().getChaveAuth()).isEqualTo("auth");
    }

    /** RD18: o mesmo aparelho passa para a conta que se inscreveu por ultimo, sem duplicar. */
    @Test
    void mesmoAparelhoPassaParaAContaQueSeInscreveuPorUltimo() {
        InscricaoPush existente = new InscricaoPush(UUID.randomUUID(), ENDPOINT, "antiga", "antiga");
        UUID outroMotorista = UUID.randomUUID();
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existente));

        service.inscrever(outroMotorista, pedido());

        assertThat(existente.getUsuarioId()).isEqualTo(outroMotorista);
        assertThat(existente.getChaveP256dh()).isEqualTo("p256dh");
        verify(repository, never()).save(any());
    }

    @Test
    void recusaEndpointSemHttpsOuSemChaves() {
        UUID motorista = UUID.randomUUID();
        assertThatThrownBy(() -> service.inscrever(motorista, new InscreverPushRequest("http://inseguro", new InscreverPushRequest.Chaves("a", "b"))))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.inscrever(motorista, new InscreverPushRequest(ENDPOINT, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void listaSoAparelhosDeMotoristasAtivos() {
        UUID ativo = UUID.randomUUID();
        when(usuarioConsultaService.idsDosMotoristasAtivos()).thenReturn(List.of(ativo));
        when(repository.findByUsuarioIdIn(List.of(ativo))).thenReturn(List.of());

        service.listarDosMotoristasAtivos();

        verify(repository).findByUsuarioIdIn(List.of(ativo));
    }

    @Test
    void semMotoristaAtivoNaoConsultaInscricoes() {
        when(usuarioConsultaService.idsDosMotoristasAtivos()).thenReturn(List.of());

        assertThat(service.listarDosMotoristasAtivos()).isEmpty();
        verify(repository, never()).findByUsuarioIdIn(any());
    }

    private static InscreverPushRequest pedido() {
        return new InscreverPushRequest(ENDPOINT, new InscreverPushRequest.Chaves("p256dh", "auth"));
    }
}
