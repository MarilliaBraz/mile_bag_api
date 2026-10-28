package com.marillia.milebag.business.viagem.service;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.pedagio.service.PracaPedagioService;
import com.marillia.milebag.business.viagem.mapper.TrajetoMapper;
import com.marillia.milebag.business.viagem.model.CalculoRota;
import com.marillia.milebag.business.viagem.model.CheckpointGps;
import com.marillia.milebag.business.viagem.model.CheckpointResponse;
import com.marillia.milebag.business.viagem.model.IniciarViagemRequest;
import com.marillia.milebag.business.viagem.model.RegistrarCheckpointRequest;
import com.marillia.milebag.business.viagem.model.TipoCheckpoint;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrajetoIniciadoResponse;
import com.marillia.milebag.business.viagem.model.TrajetoStatus;
import com.marillia.milebag.business.viagem.repository.CalculoRotaRepository;
import com.marillia.milebag.business.viagem.repository.CheckpointGpsRepository;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RotaNaoSelecionadaException;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.TipoSelecao;

/**
 * Cobre o refactor de acoplamento em ViagemService: le a rota selecionada
 * via {@link SelecaoRotaProvider} (nao mais PropostaRotaRepository direto)
 * e escreve o status da entrega via {@link EntregaService#marcarEmAndamento}
 * (nao mais EntregaRepository.save direto) - quebra o ciclo rota<->viagem.
 */
@ExtendWith(MockitoExtension.class)
class ViagemServiceTest {

    @Mock
    private EntregaService entregaService;
    @Mock
    private SelecaoRotaProvider selecaoRotaProvider;
    @Mock
    private TrajetoRepository trajetoRepository;
    @Mock
    private CalculoRotaRepository calculoRotaRepository;
    @Mock
    private CheckpointGpsRepository checkpointGpsRepository;
    @Mock
    private TrajetoMapper trajetoMapper;
    @Mock
    private MensagemService mensagemService;
    @Mock
    private PracaPedagioService pracaPedagioService;
    @Mock
    private AcompanhamentoViagem acompanhamentoViagem;

    private ViagemService viagemService;

    @BeforeEach
    void setUp() {
        viagemService = new ViagemService(
                entregaService, selecaoRotaProvider, trajetoRepository, calculoRotaRepository,
                checkpointGpsRepository, trajetoMapper, new ObjectMapper(), mensagemService, pracaPedagioService, acompanhamentoViagem);
        // lenient: nem todo teste chega a exercitar os tres saves (ex.: os
        // testes de erro em iniciarViagem retornam antes deles).
        org.mockito.Mockito.lenient().when(trajetoRepository.save(any(Trajeto.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(calculoRotaRepository.save(any(CalculoRota.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(checkpointGpsRepository.save(any(CheckpointGps.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void iniciarViagemLeRotaSelecionadaEMarcaEntregaEmAndamento() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.empty());
        SelecaoRotaProvider.RotaSelecionada selecionada = new SelecaoRotaProvider.RotaSelecionada(
                TipoSelecao.SUGERIDA, new RotaAlternativa("principal", 5000.0, 600, false));
        when(selecaoRotaProvider.buscarSelecionada(entregaId)).thenReturn(Optional.of(selecionada));
        when(trajetoMapper.toIniciadoResponse(any(), any(), any()))
                .thenReturn(new TrajetoIniciadoResponse(UUID.randomUUID(), TrajetoStatus.EM_EXECUCAO, Instant.now(), null, null));

        when(acompanhamentoViagem.criarLink(any(Trajeto.class))).thenReturn("https://app/acompanhar/abc");

        viagemService.iniciarViagem(entregaId, new IniciarViagemRequest(-23.5, -46.6));

        verify(entregaService).marcarEmAndamento(entregaId);
        verify(trajetoRepository).save(any(Trajeto.class));
        verify(calculoRotaRepository).save(any(CalculoRota.class));
        // HU31: o link vai ao passageiro no aviso de viagem iniciada e aos administradores.
        verify(mensagemService).notificarAutomatica(entregaId, TipoEventoNotificacao.VIAGEM_INICIADA, "https://app/acompanhar/abc");
        verify(acompanhamentoViagem).avisarAdministradores(entregaId, "https://app/acompanhar/abc");
    }

    /** HU31: a posicao vai para a viagem em andamento do proprio motorista. */
    @Test
    void registrarPosicaoAtualizaAViagemEmAndamentoDoMotorista() {
        UUID motoristaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(UUID.randomUUID(), motoristaId, Instant.now());
        when(trajetoRepository.findFirstByMotoristaIdAndStatusOrderByDataInicioDesc(motoristaId, TrajetoStatus.EM_EXECUCAO))
                .thenReturn(Optional.of(trajeto));

        assertThat(viagemService.registrarPosicao(motoristaId, -16.68, -49.26)).isTrue();

        assertThat(trajeto.getPosicaoLatitude()).isEqualTo(-16.68);
        assertThat(trajeto.getPosicaoLongitude()).isEqualTo(-49.26);
        assertThat(trajeto.getPosicaoRegistradaEm()).isNotNull();
        verify(trajetoRepository).save(trajeto);
    }

    @Test
    void registrarPosicaoSemViagemEmAndamentoNaoGravaNada() {
        UUID motoristaId = UUID.randomUUID();
        when(trajetoRepository.findFirstByMotoristaIdAndStatusOrderByDataInicioDesc(motoristaId, TrajetoStatus.EM_EXECUCAO))
                .thenReturn(Optional.empty());

        assertThat(viagemService.registrarPosicao(motoristaId, -16.68, -49.26)).isFalse();
        verify(trajetoRepository, never()).save(any());
    }

    /** RN16: encerrar a tentativa, com sucesso ou por ocorrencia, apaga a ultima posicao. */
    @Test
    void encerrarATentativaApagaAPosicao() {
        Trajeto entregue = new Trajeto(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        entregue.atualizarPosicao(-16.68, -49.26, Instant.now());
        entregue.consolidar(java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, "[]", 60, null);
        Trajeto naoEntregue = new Trajeto(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        naoEntregue.atualizarPosicao(-16.68, -49.26, Instant.now());
        naoEntregue.vincularOcorrencia(UUID.randomUUID(), Instant.now());

        assertThat(entregue.temPosicao()).isFalse();
        assertThat(entregue.getPosicaoLatitude()).isNull();
        assertThat(naoEntregue.temPosicao()).isFalse();
        assertThat(naoEntregue.getPosicaoLongitude()).isNull();
    }

    @Test
    void iniciarViagemLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> viagemService.iniciarViagem(entregaId, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(trajetoRepository, never()).save(any());
        verify(entregaService, never()).marcarEmAndamento(any());
    }

    @Test
    void iniciarViagemLancaConflitoQuandoTrajetoJaExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(new Trajeto(entregaId, null, Instant.now())));

        assertThatThrownBy(() -> viagemService.iniciarViagem(entregaId, null))
                .isInstanceOf(ConflitoException.class);
        verify(selecaoRotaProvider, never()).buscarSelecionada(any());
    }

    @Test
    void novaTentativaIniciaViagemNovaComOMotoristaAtual() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaDaNovaTentativa = UUID.randomUUID();
        Entrega entrega = new Entrega();
        entrega.setMotoristaResponsavel(motoristaDaNovaTentativa);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        // A viagem mais recente e a da tentativa anterior, encerrada pela ocorrencia: nao bloqueia a nova.
        Trajeto tentativaAnterior = new Trajeto(entregaId, UUID.randomUUID(), Instant.now().minusSeconds(3600));
        tentativaAnterior.vincularOcorrencia(UUID.randomUUID(), Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(tentativaAnterior));
        when(selecaoRotaProvider.buscarSelecionada(entregaId)).thenReturn(Optional.of(
                new SelecaoRotaProvider.RotaSelecionada(TipoSelecao.SUGERIDA, new RotaAlternativa("principal", 5000.0, 600, false))));

        viagemService.iniciarViagem(entregaId, null);

        org.mockito.ArgumentCaptor<Trajeto> nova = org.mockito.ArgumentCaptor.forClass(Trajeto.class);
        verify(trajetoRepository).save(nova.capture());
        assertThat(nova.getValue()).isNotSameAs(tentativaAnterior);
        assertThat(nova.getValue().getMotoristaId()).isEqualTo(motoristaDaNovaTentativa);
    }

    @Test
    void ocorrenciaEncerraAViagemComOUltimoCalculoEAVinculaSemExigirJustificativa() {
        UUID entregaId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, UUID.randomUUID(), Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));
        // Recalculo bem maior que a rota inicial: no encerramento normal exigiria justificativa (RN14).
        when(calculoRotaRepository.findByTrajetoIdOrderByDataHoraAsc(trajeto.getId())).thenReturn(List.of(
                new CalculoRota(trajeto.getId(), new java.math.BigDecimal("5.000"), new java.math.BigDecimal("5.000"), "[]", 600, TipoSelecao.SUGERIDA, Instant.now(), true),
                new CalculoRota(trajeto.getId(), new java.math.BigDecimal("20.000"), new java.math.BigDecimal("20.000"), "[]", 1800, TipoSelecao.PROPRIA, Instant.now(), false)));

        viagemService.encerrarPorOcorrencia(entregaId, ocorrenciaId);

        assertThat(trajeto.getStatus()).isEqualTo(TrajetoStatus.ENCERRADO);
        assertThat(trajeto.getKmIdaFinal()).isEqualByComparingTo("20.000");
        assertThat(trajeto.getOcorrenciaId()).isEqualTo(ocorrenciaId);
        assertThat(trajeto.getEncerradoEm()).isNotNull();
        verify(trajetoRepository).save(trajeto);
    }

    @Test
    void depoisDaOcorrenciaATentativaAtualNaoTemViagem() {
        UUID entregaId = UUID.randomUUID();
        Trajeto tentativaAnterior = new Trajeto(entregaId, UUID.randomUUID(), Instant.now());
        tentativaAnterior.vincularOcorrencia(UUID.randomUUID(), Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(tentativaAnterior));

        assertThat(viagemService.buscarTrajetoPorEntrega(entregaId)).isEmpty();
        assertThat(viagemService.marcoMaisRecente(entregaId)).isEmpty();
        assertThatThrownBy(() -> viagemService.consultar(entregaId)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void ocorrenciaSemViagemIniciadaNaoFazNada() {
        UUID entregaId = UUID.randomUUID();
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.empty());

        viagemService.encerrarPorOcorrencia(entregaId, UUID.randomUUID());

        verify(trajetoRepository, never()).save(any());
    }

    @Test
    void iniciarViagemLancaQuandoRotaNaoFoiSelecionada() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.empty());
        when(selecaoRotaProvider.buscarSelecionada(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> viagemService.iniciarViagem(entregaId, null))
                .isInstanceOf(RotaNaoSelecionadaException.class);
        verify(entregaService, never()).marcarEmAndamento(any());
        verify(trajetoRepository, never()).save(any());
    }

    @Test
    void buscarTrajetoPorEntregaDelegaParaRepository() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));

        assertThat(viagemService.buscarTrajetoPorEntrega(entregaId)).contains(trajeto);
    }

    @Test
    void registrarCalculoRotaSeTrajetoExistirRetornaFalseSemTrajeto() {
        UUID entregaId = UUID.randomUUID();
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.empty());

        boolean resultado = viagemService.registrarCalculoRotaSeTrajetoExistir(
                entregaId, new RotaAlternativa("x", 100, 10, false), TipoSelecao.SUGERIDA);

        assertThat(resultado).isFalse();
        verify(calculoRotaRepository, never()).save(any());
    }

    @Test
    void registrarCalculoRotaSeTrajetoExistirLancaQuandoJaEncerrado() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        trajeto.consolidar(java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, "[]", 10L, null);
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));

        assertThatThrownBy(() -> viagemService.registrarCalculoRotaSeTrajetoExistir(
                entregaId, new RotaAlternativa("x", 100, 10, false), TipoSelecao.SUGERIDA))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void registrarCheckpointNotificaQuandoTipoEhMarcoDeProgresso() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));
        when(trajetoMapper.toResponse(any(CheckpointGps.class))).thenReturn(new CheckpointResponse(UUID.randomUUID(), TipoCheckpoint.A_CAMINHO, null, null, Instant.now(), false));

        viagemService.registrarCheckpoint(entregaId, new RegistrarCheckpointRequest(TipoCheckpoint.A_CAMINHO, -23.5, -46.6));

        verify(mensagemService).notificarAutomatica(entregaId, TipoEventoNotificacao.A_CAMINHO, null);
    }

    /** HU20: repetir o marco registra de novo, mas nao reenvia o aviso ao passageiro. */
    @Test
    void registrarCheckpointRepetidoNaoNotificaDeNovo() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));
        when(checkpointGpsRepository.existsByTrajetoIdAndTipo(trajeto.getId(), TipoCheckpoint.A_CAMINHO)).thenReturn(true);
        when(trajetoMapper.toResponse(any(CheckpointGps.class))).thenReturn(new CheckpointResponse(UUID.randomUUID(), TipoCheckpoint.A_CAMINHO, null, null, Instant.now(), false));

        viagemService.registrarCheckpoint(entregaId, new RegistrarCheckpointRequest(TipoCheckpoint.A_CAMINHO, -23.5, -46.6));

        verify(checkpointGpsRepository).save(any(CheckpointGps.class));
        verify(mensagemService, never()).notificarAutomatica(any(), any(), any());
    }

    @Test
    void registrarCheckpointNaoNotificaParaTiposQueNaoSaoMarcoDeProgresso() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));
        when(trajetoMapper.toResponse(any(CheckpointGps.class))).thenReturn(new CheckpointResponse(UUID.randomUUID(), TipoCheckpoint.PARTIDA, null, null, Instant.now(), false));

        viagemService.registrarCheckpoint(entregaId, new RegistrarCheckpointRequest(TipoCheckpoint.PARTIDA, -23.5, -46.6));

        verify(mensagemService, never()).notificarAutomatica(any(), any(), any());
    }

    /** HU20, FR-005: a notificacao nunca bloqueia nem falha o registro do checkpoint. */
    @Test
    void registrarCheckpointNaoFalhaQuandoNotificacaoLancaExcecao() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));
        when(trajetoMapper.toResponse(any(CheckpointGps.class))).thenReturn(new CheckpointResponse(UUID.randomUUID(), TipoCheckpoint.CHEGADA, null, null, Instant.now(), false));
        org.mockito.Mockito.doThrow(new RuntimeException("falha simulada"))
                .when(mensagemService).notificarAutomatica(any(), any(), any());

        assertThat(viagemService.registrarCheckpoint(entregaId, new RegistrarCheckpointRequest(TipoCheckpoint.CHEGADA, null, null))).isNotNull();
    }

    @Test
    void marcoMaisRecenteRetornaTipoDoCheckpointMaisRecente() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, null, Instant.now());
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.of(trajeto));
        CheckpointGps chegada = new CheckpointGps(trajeto.getId(), TipoCheckpoint.CHEGADA, null, null, Instant.now(), true);
        when(checkpointGpsRepository.findFirstByTrajetoIdAndTipoInOrderByDataHoraDesc(
                trajeto.getId(), List.of(TipoCheckpoint.A_CAMINHO, TipoCheckpoint.CHEGADA)))
                .thenReturn(Optional.of(chegada));

        assertThat(viagemService.marcoMaisRecente(entregaId)).contains("CHEGADA");
    }

    @Test
    void marcoMaisRecenteVazioQuandoEntregaSemTrajeto() {
        UUID entregaId = UUID.randomUUID();
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.empty());

        assertThat(viagemService.marcoMaisRecente(entregaId)).isEmpty();
    }

    @Test
    void iniciarViagemCongelaOValorVigenteDeCadaPracaDoTracado() throws Exception {
        UUID entregaId = UUID.randomUUID();
        UUID pracaComValor = UUID.randomUUID();
        UUID pracaSemValor = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(trajetoRepository.findFirstByEntregaIdOrderByDataInicioDesc(entregaId)).thenReturn(Optional.empty());
        RotaAlternativa rota = new RotaAlternativa("principal", 5000.0, 600, true, java.util.List.of(
                new com.marillia.milebag.core.geo.PracaNaRota(pracaComValor, "Praca A", new java.math.BigDecimal("10.00")),
                new com.marillia.milebag.core.geo.PracaNaRota(pracaSemValor, "Praca B", null)));
        when(selecaoRotaProvider.buscarSelecionada(entregaId)).thenReturn(Optional.of(new SelecaoRotaProvider.RotaSelecionada(TipoSelecao.SUGERIDA, rota)));
        when(pracaPedagioService.buscarValorVigenteEm(eq(pracaComValor), any())).thenReturn(Optional.of(new java.math.BigDecimal("11.50")));
        when(pracaPedagioService.buscarValorVigenteEm(eq(pracaSemValor), any())).thenReturn(Optional.empty());
        when(trajetoMapper.toIniciadoResponse(any(), any(), any()))
                .thenReturn(new TrajetoIniciadoResponse(UUID.randomUUID(), TrajetoStatus.EM_EXECUCAO, Instant.now(), null, null));

        viagemService.iniciarViagem(entregaId, new IniciarViagemRequest(-23.5, -46.6));

        org.mockito.ArgumentCaptor<CalculoRota> salvo = org.mockito.ArgumentCaptor.forClass(CalculoRota.class);
        verify(calculoRotaRepository).save(salvo.capture());
        java.util.List<com.marillia.milebag.business.viagem.model.TrechoPedagio> trechos = new ObjectMapper().readValue(
                salvo.getValue().getPracasPedagio(), new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        assertThat(trechos).hasSize(2);
        assertThat(trechos.get(0).pracaId()).isEqualTo(pracaComValor);
        assertThat(trechos.get(0).valorUnitario()).isEqualByComparingTo("11.50");
        assertThat(trechos.get(0).valorCadastrado()).isTrue();
        // O valor congelado e o VIGENTE na data do calculo, nao o que veio na proposta (pode ter mudado desde entao).
        assertThat(trechos.get(1).valorCadastrado()).isFalse();
        assertThat(trechos.get(1).valorUnitario()).isNull();
    }
}
