package com.marillia.milebag.business.retirada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.fotoentrega.model.TipoFotoEntrega;
import com.marillia.milebag.business.fotoentrega.service.FotoEntregaService;
import com.marillia.milebag.business.retirada.model.RetiradaBagagem;
import com.marillia.milebag.business.retirada.repository.RetiradaBagagemRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class RetiradaBagagemServiceTest {

    @Mock
    private RetiradaBagagemRepository retiradaBagagemRepository;
    @Mock
    private EntregaService entregaService;
    @Mock
    private FotoEntregaService fotoEntregaService;
    @Mock
    private com.marillia.milebag.business.correcao.service.CorrecaoEntregaService correcaoEntregaService;

    private RetiradaBagagemService retiradaBagagemService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        retiradaBagagemService = servico(Instant.now().minusSeconds(60));
    }

    private RetiradaBagagemService servico(Instant transicaoAte) {
        return new RetiradaBagagemService(retiradaBagagemRepository, entregaService, fotoEntregaService, correcaoEntregaService, transicaoAte);
    }

    private final UUID entregaId = UUID.randomUUID();
    private final UUID motorista = UUID.randomUUID();
    private final MockMultipartFile foto = new MockMultipartFile("foto", "mala.jpg", "image/jpeg", new byte[] {1, 2});

    private void entrega(EntregaStatus status, UUID responsavel) {
        Entrega entrega = new Entrega();
        entrega.setId(entregaId);
        entrega.setStatus(status);
        entrega.setMotoristaResponsavel(responsavel);
        org.mockito.Mockito.lenient().when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        // V51: a correcao trava a entrega.
        org.mockito.Mockito.lenient().when(entregaService.travarParaCorrigir(entregaId)).thenReturn(entrega);
    }

    private void salvarDevolvendoComId() {
        when(retiradaBagagemRepository.saveAndFlush(any(RetiradaBagagem.class))).thenAnswer(inv -> {
            RetiradaBagagem retirada = inv.getArgument(0);
            retirada.setId(UUID.randomUUID());
            return retirada;
        });
    }

    @Test
    void registraAFotoAsAvariasEOndeOMotoristaEstava() {
        entrega(EntregaStatus.ATRIBUIDA, motorista);
        salvarDevolvendoComId();
        var avaria = new MockMultipartFile("avarias", "a.jpg", "image/jpeg", new byte[] {3});

        var resposta = retiradaBagagemService.registrar(
                entregaId, motorista, foto, List.of(avaria), -16.63, -49.22, "2026-11-21T10:00:00Z", "local-1", " roda quebrada ");

        ArgumentCaptor<RetiradaBagagem> salva = ArgumentCaptor.forClass(RetiradaBagagem.class);
        verify(retiradaBagagemRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getLatitude()).isEqualTo(-16.63);
        assertThat(salva.getValue().getObservacaoAvaria()).isEqualTo("roda quebrada");
        assertThat(salva.getValue().getDataHora()).isEqualTo(Instant.parse("2026-11-21T10:00:00Z"));
        assertThat(salva.getValue().isRegistradoOffline()).isTrue();
        verify(fotoEntregaService).salvarDaRetirada(eq(entregaId), eq(resposta.id()), eq(TipoFotoEntrega.RETIRADA), eq(foto), any());
        verify(fotoEntregaService).salvarDaRetirada(eq(entregaId), eq(resposta.id()), eq(TipoFotoEntrega.AVARIA_RETIRADA), eq(avaria), any());
        assertThat(resposta.comLocalizacao()).isTrue();
    }

    /** H: a posicao so e registrada - sem ela (GPS falhou no aeroporto) a retirada vale do mesmo jeito. */
    @Test
    void semPosicaoARetiradaTambemVale() {
        entrega(EntregaStatus.ATRIBUIDA, motorista);
        salvarDevolvendoComId();

        var resposta = retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, null, null);

        assertThat(resposta.comLocalizacao()).isFalse();
    }

    @Test
    void fotoEObrigatoria() {
        entrega(EntregaStatus.ATRIBUIDA, motorista);

        assertThatThrownBy(() -> retiradaBagagemService.registrar(entregaId, motorista, null, null, null, null, null, null, null))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("foto");
        verify(retiradaBagagemRepository, never()).saveAndFlush(any());
    }

    @Test
    void soOMotoristaResponsavel() {
        entrega(EntregaStatus.ATRIBUIDA, UUID.randomUUID());

        assertThatThrownBy(() -> retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, null, null))
                .isInstanceOf(ConflitoException.class);
        verifyNoInteractions(fotoEntregaService);
    }

    @Test
    void soAntesDeIniciarAViagem() {
        entrega(EntregaStatus.EM_ANDAMENTO, motorista);

        assertThatThrownBy(() -> retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, null, null))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void entregaCanceladaMostraAMensagemDaEmpresa() {
        entrega(EntregaStatus.CANCELADA, null);

        assertThatThrownBy(() -> retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, null, null))
                .isInstanceOf(ConflitoException.class).hasMessage(EntregaService.MENSAGEM_CANCELADA);
    }

    @Test
    void umaRetiradaPorTentativa() {
        entrega(EntregaStatus.ATRIBUIDA, motorista);
        when(retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motorista))
                .thenReturn(Optional.of(new RetiradaBagagem(entregaId, motorista, null, null, null, Instant.now(), false, null)));

        assertThatThrownBy(() -> retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, null, null))
                .isInstanceOf(ConflitoException.class);
    }

    /** Sem internet, o mesmo evento pode chegar duas vezes: devolve a retirada ja gravada, sem duplicar fotos. */
    @Test
    void reenvioDoMesmoIdLocalEIdempotente() {
        entrega(EntregaStatus.EM_ANDAMENTO, motorista);
        RetiradaBagagem existente = new RetiradaBagagem(entregaId, motorista, null, null, null, Instant.now(), true, "local-1");
        existente.setId(UUID.randomUUID());
        when(retiradaBagagemRepository.findByEntregaIdAndIdLocal(entregaId, "local-1")).thenReturn(Optional.of(existente));

        var resposta = retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, "local-1", null);

        assertThat(resposta.id()).isEqualTo(existente.getId());
        verifyNoInteractions(fotoEntregaService);
    }

    @Test
    void iniciarAViagemSemRetiradaERecusado() {
        when(retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motorista))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> retiradaBagagemService.vincularAoTrajeto(entregaId, motorista, UUID.randomUUID(), false))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("retirada");
    }

    @Test
    void iniciarAViagemVinculaARetiradaPendente() {
        RetiradaBagagem pendente = new RetiradaBagagem(entregaId, motorista, null, null, null, Instant.now(), false, null);
        when(retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motorista))
                .thenReturn(Optional.of(pendente));
        UUID trajeto = UUID.randomUUID();

        retiradaBagagemService.vincularAoTrajeto(entregaId, motorista, trajeto, false);

        assertThat(pendente.getTrajetoId()).isEqualTo(trajeto);
        verify(retiradaBagagemRepository).save(pendente);
    }

    /** M3: viagem guardada por app antigo na fila offline inicia sem retirada, so ate a data de corte e so pela fila. */
    @Test
    void viagemAntigaDaFilaOfflineIniciaSemRetiradaSoNaTransicao() {
        when(retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motorista))
                .thenReturn(Optional.empty());
        RetiradaBagagemService naTransicao = servico(Instant.now().plusSeconds(3600));

        naTransicao.vincularAoTrajeto(entregaId, motorista, UUID.randomUUID(), true);
        assertThatThrownBy(() -> naTransicao.vincularAoTrajeto(entregaId, motorista, UUID.randomUUID(), false))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> retiradaBagagemService.vincularAoTrajeto(entregaId, motorista, UUID.randomUUID(), true))
                .isInstanceOf(RegraNegocioException.class);
    }

    /** M1: dispensa ou cancelamento antes da viagem descarta a retirada pendente - quem assumir de novo retira outra vez. */
    @Test
    void descartarPendentesMarcaAsRetiradasSemViagem() {
        RetiradaBagagem pendente = new RetiradaBagagem(entregaId, motorista, null, null, null, Instant.now(), false, null);
        when(retiradaBagagemRepository.findByEntregaIdAndTrajetoIdIsNullAndDescartadaEmIsNull(entregaId)).thenReturn(List.of(pendente));

        retiradaBagagemService.descartarPendentes(entregaId);

        assertThat(pendente.getDescartadaEm()).isNotNull();
        verify(retiradaBagagemRepository).save(pendente);
    }

    /** M2: dois envios simultaneos - o indice unico da pendente vira 409, nao erro 500. */
    @Test
    void retiradaSimultaneaViraConflito() {
        entrega(EntregaStatus.ATRIBUIDA, motorista);
        when(retiradaBagagemRepository.saveAndFlush(any(RetiradaBagagem.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_retirada_bagagem_pendente"));

        assertThatThrownBy(() -> retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, null, null))
                .isInstanceOf(ConflitoException.class);
        verifyNoInteractions(fotoEntregaService);
    }

    /** L2: o idLocal de outro motorista nao devolve a retirada dele. */
    @Test
    void idLocalDeOutroMotoristaNaoDevolveARetiradaDele() {
        entrega(EntregaStatus.ATRIBUIDA, UUID.randomUUID());
        RetiradaBagagem deOutro = new RetiradaBagagem(entregaId, UUID.randomUUID(), null, null, null, Instant.now(), true, "local-x");
        when(retiradaBagagemRepository.findByEntregaIdAndIdLocal(entregaId, "local-x")).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> retiradaBagagemService.registrar(entregaId, motorista, foto, null, null, null, null, "local-x", null))
                .isInstanceOf(ConflitoException.class);
    }

    /** V51: corrigir a retirada antes de iniciar a viagem - foto nova com motivo; a antiga fica substituida. */
    @Test
    void corrigirAFotoDaRetiradaSubstituiERegistraACorrecao() {
        entrega(EntregaStatus.ATRIBUIDA, motorista);
        RetiradaBagagem pendente = new RetiradaBagagem(entregaId, motorista, null, null, null, Instant.now(), false, null);
        pendente.setId(UUID.randomUUID());
        when(retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motorista))
                .thenReturn(Optional.of(pendente));
        UUID motivo = UUID.randomUUID();

        retiradaBagagemService.corrigir(entregaId, motorista, foto, false, List.of(), null, motivo, "estava tremida");

        verify(correcaoEntregaService).exigirMotivo(motivo, "estava tremida");
        verify(fotoEntregaService).substituir(eq(entregaId), eq(pendente.getId()), eq(null), eq(TipoFotoEntrega.RETIRADA), eq(List.of(foto)), any());
        verify(correcaoEntregaService).registrar(eq(entregaId), eq(com.marillia.milebag.business.correcao.model.EtapaCorrecao.FOTO_RETIRADA),
                eq(com.marillia.milebag.business.correcao.model.MomentoCorrecao.APOS_REGISTRO), eq(motivo), eq("estava tremida"), any(), any(),
                eq(motorista), any());
    }

    @Test
    void corrigirRetiradaSemRetiradaPendenteERecusado() {
        entrega(EntregaStatus.EM_ANDAMENTO, motorista);

        assertThatThrownBy(() -> retiradaBagagemService.corrigir(entregaId, motorista, foto, false, List.of(), null, UUID.randomUUID(), null))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("A retirada so pode ser corrigida depois de registrada e antes de iniciar a viagem.");
        verifyNoInteractions(fotoEntregaService);
    }

    @Test
    void outroMotoristaNaoCorrigeARetirada() {
        entrega(EntregaStatus.ATRIBUIDA, UUID.randomUUID());

        assertThatThrownBy(() -> retiradaBagagemService.corrigir(entregaId, motorista, foto, false, List.of(), null, UUID.randomUUID(), null))
                .isInstanceOf(ConflitoException.class);
        verifyNoInteractions(fotoEntregaService, correcaoEntregaService);
    }

    @Test
    void corrigirSemNadaParaCorrigirERecusado() {
        entrega(EntregaStatus.ATRIBUIDA, motorista);
        when(retiradaBagagemRepository.findFirstByEntregaIdAndMotoristaIdAndTrajetoIdIsNullAndDescartadaEmIsNullOrderByDataHoraDesc(entregaId, motorista))
                .thenReturn(Optional.of(new RetiradaBagagem(entregaId, motorista, null, null, null, Instant.now(), false, null)));

        assertThatThrownBy(() -> retiradaBagagemService.corrigir(entregaId, motorista, null, false, List.of(), null, UUID.randomUUID(), null))
                .isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(fotoEntregaService);
    }
}
