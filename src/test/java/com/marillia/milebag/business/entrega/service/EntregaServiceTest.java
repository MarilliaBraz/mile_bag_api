package com.marillia.milebag.business.entrega.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaRepository;
import com.marillia.milebag.business.entrega.mapper.EntregaMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Cobre os metodos adicionados no refactor de acoplamento: EntregaService
 * passa a ser o unico ponto de escrita em Entrega para outros dominios
 * (viagem/comprovante/ocorrencia chamavam entregaRepository.save direto
 * antes) e passa a consultar Bdo via BdoService em vez de BdoRepository.
 */
@ExtendWith(MockitoExtension.class)
class EntregaServiceTest {

    @Mock
    private EntregaRepository entregaRepository;
    @Mock
    private BdoService bdoService;
    @Mock
    private EntregaMapper entregaMapper;
    @Mock
    private BagagemCaronaRepository bagagemCaronaRepository;

    @InjectMocks
    private EntregaService entregaService;

    private Entrega novaEntrega(UUID id, EntregaStatus status) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(status);
        return entrega;
    }

    @Test
    void marcarEmAndamentoAtualizaStatusEPersiste() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.ATRIBUIDA);
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.of(entrega));

        entregaService.marcarEmAndamento(entregaId);

        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.EM_ANDAMENTO);
        verify(entregaRepository).save(entrega);
    }

    @Test
    void marcarEncerradaComSucessoAtualizaStatusEPersiste() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.EM_ANDAMENTO);
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.of(entrega));

        entregaService.marcarEncerradaComSucesso(entregaId);

        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.ENCERRADA_COM_SUCESSO);
        verify(entregaRepository).save(entrega);
    }

    @Test
    void reabrirParaNovaTentativaLimpaMotoristaEReabre() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.EM_ANDAMENTO);
        entrega.setMotoristaResponsavel(UUID.randomUUID());
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.of(entrega));

        entregaService.reabrirParaNovaTentativa(entregaId);

        assertThat(entrega.getStatus()).isEqualTo(EntregaStatus.REABERTA);
        assertThat(entrega.getMotoristaResponsavel()).isNull();
        verify(entregaRepository).save(entrega);
    }

    @Test
    void marcarEmAndamentoLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entregaService.marcarEmAndamento(entregaId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(entregaRepository, never()).save(any());
    }

    @Test
    void buscarPorIdDelegaParaRepository() {
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = novaEntrega(entregaId, EntregaStatus.AGUARDANDO_ATRIBUICAO);
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.of(entrega));

        assertThat(entregaService.buscarPorId(entregaId)).contains(entrega);
    }

    @Test
    void autoatribuirConsultaBdoViaBdoServiceEExigeConfirmado() {
        UUID bdoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setStatusConferencia(StatusConferencia.PENDENTE);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));

        assertThatThrownBy(() -> entregaService.autoatribuir(bdoId, motoristaId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(entregaRepository, never()).autoatribuirSeDisponivel(any(), any(), any(), any());
    }

    @Test
    void autoatribuirLancaConflitoQuandoNenhumaLinhaAtualizada() {
        UUID bdoId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));
        when(entregaRepository.autoatribuirSeDisponivel(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> entregaService.autoatribuir(bdoId, motoristaId))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void autoatribuirRecusaBdoJaVinculadoComoBagagemCarona() {
        UUID bdoId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setStatusConferencia(StatusConferencia.CONFIRMADO);
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));
        when(bagagemCaronaRepository.findByBdoId(bdoId)).thenReturn(Optional.of(new BagagemCarona(bdoId, UUID.randomUUID(), BigDecimal.ONE)));

        assertThatThrownBy(() -> entregaService.autoatribuir(bdoId, UUID.randomUUID())).isInstanceOf(ConflitoException.class);
        verify(entregaRepository, never()).autoatribuirSeDisponivel(any(), any(), any(), any());
    }
}
