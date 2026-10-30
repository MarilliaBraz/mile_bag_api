package com.marillia.milebag.business.mensagem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.mensagem.channel.EmailChannel;
import com.marillia.milebag.business.mensagem.channel.TelegramClient;
import com.marillia.milebag.business.mensagem.model.AutorMensagem;
import com.marillia.milebag.business.mensagem.model.CanalMensagem;
import com.marillia.milebag.business.mensagem.model.ConversaResponse;
import com.marillia.milebag.business.mensagem.model.Mensagem;
import com.marillia.milebag.business.mensagem.model.MensagemResponse;
import com.marillia.milebag.business.mensagem.model.StatusEnvioMensagem;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.model.VinculoTelegram;
import com.marillia.milebag.business.mensagem.repository.MensagemRepository;
import com.marillia.milebag.business.mensagem.repository.VinculoTelegramRepository;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Cobre HU18 (US1): conversa por entrega, escolha automatica de canal e notificacao automatica. */
@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    @Mock
    private MensagemRepository mensagemRepository;
    @Mock
    private VinculoTelegramRepository vinculoTelegramRepository;
    @Mock
    private EntregaService entregaService;
    @Mock
    private BdoService bdoService;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private EmailChannel emailChannel;

    private MensagemService mensagemService;

    @BeforeEach
    void setUp() {
        mensagemService = new MensagemService(
                mensagemRepository, vinculoTelegramRepository, entregaService, bdoService, telegramClient, emailChannel, "America/Sao_Paulo");
        org.mockito.Mockito.lenient().when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Entrega entregaDe(UUID id, UUID motoristaId, UUID bdoId) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setMotoristaResponsavel(motoristaId);
        entrega.setBdoId(bdoId);
        entrega.setStatus(EntregaStatus.EM_ANDAMENTO);
        return entrega;
    }

    private Bdo bdoComEmail(UUID id, String email) {
        Bdo bdo = new Bdo();
        bdo.setId(id);
        bdo.setNumero("BDO-1");
        bdo.setEmailPassageiro(email);
        return bdo;
    }

    @Test
    void consultarConversaSemEmailNaoTemNenhumContatoEConversaVazia() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId, bdoId)));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, null)));

        ConversaResponse conversa = mensagemService.consultarConversa(entregaId, motoristaId);

        assertThat(conversa.contatoDisponivel().email()).isFalse();
        assertThat(conversa.contatoDisponivel().telegram()).isFalse();
        assertThat(conversa.mensagens()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(mensagemRepository);
    }

    @Test
    void consultarConversaComEmailSemVinculoTelegram() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId, bdoId)));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, "passageiro@exemplo.com")));
        when(vinculoTelegramRepository.existsByEntregaId(entregaId)).thenReturn(false);
        when(mensagemRepository.findByEntregaIdOrderByCriadoEmAsc(entregaId)).thenReturn(List.of());

        ConversaResponse conversa = mensagemService.consultarConversa(entregaId, motoristaId);

        assertThat(conversa.contatoDisponivel().email()).isTrue();
        assertThat(conversa.contatoDisponivel().telegram()).isFalse();
    }

    @Test
    void consultarConversaLancaAcessoNegadoQuandoNaoPertenceAoMotorista() {
        UUID entregaId = UUID.randomUUID();
        UUID donoReal = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, donoReal, UUID.randomUUID())));

        assertThatThrownBy(() -> mensagemService.consultarConversa(entregaId, UUID.randomUUID()))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void enviarManualRecusaConteudoVazio() {
        assertThatThrownBy(() -> mensagemService.enviarManual(UUID.randomUUID(), UUID.randomUUID(), " "))
                .isInstanceOf(RegraNegocioException.class);
        org.mockito.Mockito.verifyNoInteractions(entregaService);
    }

    @Test
    void enviarManualRecusaQuandoSemContatoDisponivel() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId, bdoId)));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, "")));

        assertThatThrownBy(() -> mensagemService.enviarManual(entregaId, motoristaId, "oi"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void enviarManualEnviaSoPorEmailQuandoSemVinculoTelegram() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId, bdoId)));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, "passageiro@exemplo.com")));
        when(vinculoTelegramRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
        when(emailChannel.enviar(eq(entregaId), any(), any(), any(), eq(true))).thenReturn(true);

        MensagemResponse resposta = mensagemService.enviarManual(entregaId, motoristaId, "Estou chegando");

        assertThat(resposta.autor()).isEqualTo(AutorMensagem.MOTORISTA);
        assertThat(resposta.canal()).isEqualTo(CanalMensagem.EMAIL);
        assertThat(resposta.statusEnvio()).isEqualTo(StatusEnvioMensagem.ENVIADA);
        verify(mensagemRepository, times(1)).save(any(Mensagem.class));
        org.mockito.Mockito.verifyNoInteractions(telegramClient);
    }

    @Test
    void enviarManualEnviaPorEmailETelegramQuandoAmbosDisponiveis() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId, bdoId)));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, "passageiro@exemplo.com")));
        VinculoTelegram vinculo = new VinculoTelegram(entregaId, 999L);
        when(vinculoTelegramRepository.findByEntregaId(entregaId)).thenReturn(Optional.of(vinculo));
        when(emailChannel.enviar(eq(entregaId), any(), any(), any(), eq(false))).thenReturn(true);
        when(telegramClient.enviar(eq(999L), any())).thenReturn(true);

        mensagemService.enviarManual(entregaId, motoristaId, "Estou chegando");

        verify(mensagemRepository, times(2)).save(any(Mensagem.class));
        verify(telegramClient).enviar(999L, "Estou chegando");
        // Sem vinculo ainda por confirmar -> nao inclui o convite (ja existe vinculo).
        verify(emailChannel).enviar(eq(entregaId), eq("passageiro@exemplo.com"), any(), any(), eq(false));
    }

    @Test
    void notificarAutomaticaNaoFazNadaQuandoSemContato() {
        UUID entregaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        Entrega entrega = entregaDe(entregaId, UUID.randomUUID(), bdoId);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, null)));

        mensagemService.notificarAutomatica(entregaId, TipoEventoNotificacao.VIAGEM_INICIADA);

        org.mockito.Mockito.verifyNoInteractions(mensagemRepository, emailChannel, telegramClient);
    }

    @Test
    void notificarAutomaticaPersisteMensagemDeSistema() {
        UUID entregaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        Entrega entrega = entregaDe(entregaId, UUID.randomUUID(), bdoId);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, "passageiro@exemplo.com")));
        when(vinculoTelegramRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
        when(emailChannel.enviar(any(), any(), any(), any(), anyBoolean())).thenReturn(true);

        mensagemService.notificarAutomatica(entregaId, TipoEventoNotificacao.CHEGADA);

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(captor.capture());
        assertThat(captor.getValue().getAutor()).isEqualTo(AutorMensagem.SISTEMA);
        assertThat(captor.getValue().getTipoEvento()).isEqualTo(TipoEventoNotificacao.CHEGADA);
    }

    /** HU31: o link "Acompanhe sua bagagem" vai no aviso de viagem iniciada, no e-mail e no Telegram. */
    @Test
    void notificarViagemIniciadaLevaOLinkDeAcompanhamentoNosDoisCanais() {
        UUID entregaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, UUID.randomUUID(), bdoId)));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, "passageiro@exemplo.com")));
        when(vinculoTelegramRepository.findByEntregaId(entregaId)).thenReturn(Optional.of(new VinculoTelegram(entregaId, 777L)));
        when(emailChannel.enviar(any(), any(), any(), any(), anyBoolean())).thenReturn(true);
        when(telegramClient.enviar(eq(777L), any())).thenReturn(true);

        mensagemService.notificarAutomatica(entregaId, TipoEventoNotificacao.VIAGEM_INICIADA, "https://app/acompanhar/abc");

        String esperado = "\n\nAcompanhe sua bagagem: https://app/acompanhar/abc";
        verify(emailChannel).enviar(eq(entregaId), eq("passageiro@exemplo.com"), any(), org.mockito.ArgumentMatchers.endsWith(esperado), eq(false));
        verify(telegramClient).enviar(eq(777L), org.mockito.ArgumentMatchers.endsWith(esperado));
    }

    @Test
    void notificarReagendamentoInformaAJanelaNoFusoDoTenant() {
        UUID entregaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        Entrega entrega = entregaDe(entregaId, null, bdoId);
        entrega.definirJanelaReagendamento(
                new JanelaReagendamento(Instant.parse("2026-10-23T17:00:00Z"), Instant.parse("2026-10-23T19:00:00Z")));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdoComEmail(bdoId, "passageiro@exemplo.com")));
        when(vinculoTelegramRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
        when(emailChannel.enviar(any(), any(), any(), any(), anyBoolean())).thenReturn(true);

        mensagemService.notificarAutomatica(entregaId, TipoEventoNotificacao.REAGENDAMENTO_REGISTRADO);

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(captor.capture());
        assertThat(captor.getValue().getConteudo()).contains("foi reagendada para 23/10/2026, entre 14:00 e 16:00, como combinado.");
    }

    @Test
    void confirmarVinculoTelegramCriaQuandoNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(vinculoTelegramRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());

        mensagemService.confirmarVinculoTelegram(entregaId, 123L);

        verify(vinculoTelegramRepository).save(any(VinculoTelegram.class));
        verify(telegramClient).enviar(eq(123L), any());
    }

    @Test
    void confirmarVinculoTelegramNaoDuplicaQuandoJaExiste() {
        UUID entregaId = UUID.randomUUID();
        when(vinculoTelegramRepository.findByEntregaId(entregaId)).thenReturn(Optional.of(new VinculoTelegram(entregaId, 123L)));

        mensagemService.confirmarVinculoTelegram(entregaId, 123L);

        verify(vinculoTelegramRepository, never()).save(any());
        verify(telegramClient).enviar(eq(123L), any());
    }

    @Test
    void processarAtualizacaoTelegramRetornaFalseQuandoChatDesconhecido() {
        when(vinculoTelegramRepository.findByChatId(555L)).thenReturn(List.of());

        assertThat(mensagemService.processarAtualizacaoTelegram(555L, "oi")).isFalse();
        org.mockito.Mockito.verifyNoInteractions(mensagemRepository);
    }

    @Test
    void processarAtualizacaoTelegramAssociaExatoComUmSoVinculo() {
        UUID entregaId = UUID.randomUUID();
        VinculoTelegram vinculo = new VinculoTelegram(entregaId, 555L);
        when(vinculoTelegramRepository.findByChatId(555L)).thenReturn(List.of(vinculo));

        boolean resultado = mensagemService.processarAtualizacaoTelegram(555L, "cheguei em casa");

        assertThat(resultado).isTrue();
        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(captor.capture());
        assertThat(captor.getValue().getEntregaId()).isEqualTo(entregaId);
        assertThat(captor.getValue().getAutor()).isEqualTo(AutorMensagem.PASSAGEIRO);
        assertThat(captor.getValue().getStatusEnvio()).isEqualTo(StatusEnvioMensagem.RECEBIDA);
    }

    /** research.md item 3: 2+ entregas em aberto para o mesmo chatId - ambiguidade resolvida pelo vinculo mais recente. */
    @Test
    void processarAtualizacaoTelegramResolveAmbiguidadeComVinculoMaisRecenteQuandoDuasEmAberto() {
        UUID entregaAntiga = UUID.randomUUID();
        UUID entregaRecente = UUID.randomUUID();
        VinculoTelegram vinculoAntigo = new VinculoTelegram(entregaAntiga, 555L);
        VinculoTelegram vinculoRecente = new VinculoTelegram(entregaRecente, 555L);
        // criadoEm so e preenchido por @CreationTimestamp no persist real (Hibernate) -
        // fixado aqui via reflexao para exercitar o desempate por "mais recente".
        ReflectionTestUtils.setField(vinculoAntigo, "criadoEm", Instant.parse("2026-09-01T10:00:00Z"));
        ReflectionTestUtils.setField(vinculoRecente, "criadoEm", Instant.parse("2026-09-02T10:00:00Z"));
        when(vinculoTelegramRepository.findByChatId(555L)).thenReturn(List.of(vinculoAntigo, vinculoRecente));
        when(entregaService.buscarPorId(entregaAntiga)).thenReturn(Optional.of(entregaDe(entregaAntiga, UUID.randomUUID(), UUID.randomUUID())));
        when(entregaService.buscarPorId(entregaRecente)).thenReturn(Optional.of(entregaDe(entregaRecente, UUID.randomUUID(), UUID.randomUUID())));

        mensagemService.processarAtualizacaoTelegram(555L, "oi de novo");

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(captor.capture());
        assertThat(captor.getValue().getEntregaId()).isEqualTo(entregaRecente);
    }

    @Test
    void registrarRecebidaEmailPersisteMensagemDoPassageiro() {
        UUID entregaId = UUID.randomUUID();

        mensagemService.registrarRecebidaEmail(entregaId, "Obrigado!");

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(captor.capture());
        assertThat(captor.getValue().getCanal()).isEqualTo(CanalMensagem.EMAIL);
        assertThat(captor.getValue().getAutor()).isEqualTo(AutorMensagem.PASSAGEIRO);
    }
}
