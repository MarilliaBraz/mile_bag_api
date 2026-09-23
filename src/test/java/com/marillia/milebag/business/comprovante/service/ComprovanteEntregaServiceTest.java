package com.marillia.milebag.business.comprovante.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.repository.ComprovanteEntregaRepository;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;

/**
 * Cobre o refactor de acoplamento em ComprovanteEntregaService: consulta e
 * encerra a Entrega via EntregaService (antes escrevia entrega.setStatus e
 * chamava entregaRepository.save direto, por fora do dominio de Entrega).
 */
@ExtendWith(MockitoExtension.class)
class ComprovanteEntregaServiceTest {

    @Mock
    private ComprovanteEntregaRepository comprovanteEntregaRepository;
    @Mock
    private EntregaService entregaService;
    @Mock
    private ArmazenamentoImagemService armazenamentoImagemService;
    @Mock
    private ViagemService viagemService;

    @InjectMocks
    private ComprovanteEntregaService comprovanteEntregaService;

    @Test
    void registrarEncerraEntregaViaEntregaServiceAposConsolidarTrajeto() throws Exception {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(comprovanteEntregaRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
        when(armazenamentoImagemService.salvar(any(), any())).thenReturn("comprovantes/foto.jpg");
        when(comprovanteEntregaRepository.save(any(ComprovanteEntrega.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile foto = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});

        comprovanteEntregaService.registrar(
                entregaId, foto, "Fulano de Tal", Instant.now().toString(), -23.5, -46.6, null, null);

        verify(viagemService).registrarCheckpoint(any(), any());
        verify(viagemService).encerrarTrajeto(any(), any());
        verify(entregaService).marcarEncerradaComSucesso(entregaId);
    }

    @Test
    void registrarLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());
        MockMultipartFile foto = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[] {1});

        assertThatThrownBy(() -> comprovanteEntregaService.registrar(
                entregaId, foto, "Fulano", Instant.now().toString(), -23.5, -46.6, null, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(entregaService, never()).marcarEncerradaComSucesso(any());
        verify(comprovanteEntregaRepository, never()).save(any());
    }

    @Test
    void listarPorPeriodoDelegaParaRepository() {
        Instant inicio = Instant.now().minusSeconds(3600);
        Instant fim = Instant.now();
        when(comprovanteEntregaRepository.findByDataHoraBetween(inicio, fim))
                .thenReturn(java.util.List.of());

        assertThat(comprovanteEntregaService.listarPorPeriodo(inicio, fim)).isEmpty();
    }
}
