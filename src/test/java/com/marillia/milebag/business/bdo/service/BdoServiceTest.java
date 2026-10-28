package com.marillia.milebag.business.bdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.marillia.milebag.business.bdo.mapper.BdoMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoRecebidoPorEmail;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;

/**
 * Cobre {@link BdoService#buscarPorId}, o ponto de leitura publico
 * adicionado no refactor de acoplamento para que outros dominios (entrega,
 * rota, entrega.bagagemcarona) parem de injetar BdoRepository direto.
 */
@ExtendWith(MockitoExtension.class)
class BdoServiceTest {

    @Mock
    private BdoRepository bdoRepository;
    @Mock
    private ArmazenamentoImagemService armazenamentoImagemService;
    @Mock
    private BdoOcrService bdoOcrService;
    @Mock
    private BdoMapper bdoMapper;
    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private BdoService bdoService;

    @Test
    void buscarPorIdDelegaParaRepository() {
        UUID id = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setId(id);
        when(bdoRepository.findById(id)).thenReturn(Optional.of(bdo));

        assertThat(bdoService.buscarPorId(id)).contains(bdo);
    }

    @Test
    void buscarPorIdRetornaVazioQuandoNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(bdoRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(bdoService.buscarPorId(id)).isEmpty();
    }

    /** HU32: so a via por e-mail avisa os motoristas (o evento e o gatilho do aviso). */
    @Test
    void criarAutomaticoPublicaOBdoRecebidoPorEmail() {
        when(bdoRepository.save(any(Bdo.class))).thenAnswer(invocacao -> {
            Bdo salvo = invocacao.getArgument(0);
            salvo.setId(UUID.randomUUID());
            return salvo;
        });

        bdoService.criarAutomatico(new CamposExtraidosBdo("GYNLA12342", null, null, null, null, null, null));

        org.mockito.ArgumentCaptor<BdoRecebidoPorEmail> evento = org.mockito.ArgumentCaptor.forClass(BdoRecebidoPorEmail.class);
        org.mockito.Mockito.verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().numero()).isEqualTo("GYNLA12342");
        assertThat(evento.getValue().bdoId()).isNotNull();
    }

    @Test
    void capturarGuardaQuemFotografou() throws IOException {
        UUID motoristaId = UUID.randomUUID();
        MockMultipartFile foto = new MockMultipartFile("imagem", "bdo.png", "image/png", new byte[] {1, 2, 3});
        when(bdoOcrService.extrair(foto)).thenReturn(new CamposExtraidosBdo("GYN1", null, null, null, null, null, null));
        when(armazenamentoImagemService.salvar(any(), eq(foto))).thenReturn("tenant/bdo.png");
        when(bdoRepository.save(any(Bdo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        bdoService.capturar(foto, motoristaId);

        ArgumentCaptor<Bdo> salvo = ArgumentCaptor.forClass(Bdo.class);
        verify(bdoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getCapturadoPor()).isEqualTo(motoristaId);
        assertThat(salvo.getValue().getStatusConferencia()).isEqualTo(StatusConferencia.PENDENTE);
        // HU32: BDO por fotografia nao avisa os motoristas - quem fotografou ja sabe dele.
        org.mockito.Mockito.verifyNoInteractions(eventos);
    }

    @Test
    void listarPendentesDeConferenciaTrazSoAsFotosPendentesDoMotorista() {
        UUID motoristaId = UUID.randomUUID();
        Bdo pendente = bdo(BdoOrigem.FOTO_MANUAL, StatusConferencia.PENDENTE, "tenant/foto.png");
        BdoResponse resposta = new BdoResponse(pendente.getId(), BdoOrigem.FOTO_MANUAL, null, "tenant/foto.png", StatusConferencia.PENDENTE, null, List.of());
        when(bdoRepository.findByCapturadoPorAndOrigemAndStatusConferenciaOrderByDataCapturaAsc(
                motoristaId, BdoOrigem.FOTO_MANUAL, StatusConferencia.PENDENTE)).thenReturn(List.of(pendente));
        when(bdoMapper.toResponse(pendente)).thenReturn(resposta);

        assertThat(bdoService.listarPendentesDeConferencia(motoristaId)).containsExactly(resposta);
    }

    @Test
    void descartarCapturaPendenteExcluiRegistroEImagem() throws IOException {
        Bdo bdo = bdo(BdoOrigem.FOTO_MANUAL, StatusConferencia.PENDENTE, "tenant/foto.png");
        when(bdoRepository.findById(bdo.getId())).thenReturn(Optional.of(bdo));

        bdoService.descartarCaptura(bdo.getId());

        verify(bdoRepository).delete(bdo);
        verify(armazenamentoImagemService).remover("tenant/foto.png");
    }

    @Test
    void descartarCapturaRecusaBdoConfirmado() throws IOException {
        Bdo bdo = bdo(BdoOrigem.FOTO_MANUAL, StatusConferencia.CONFIRMADO, "tenant/foto.png");
        when(bdoRepository.findById(bdo.getId())).thenReturn(Optional.of(bdo));

        assertThatThrownBy(() -> bdoService.descartarCaptura(bdo.getId())).isInstanceOf(ConflitoException.class);
        verify(bdoRepository, never()).delete(any());
        verify(armazenamentoImagemService, never()).remover(any());
    }

    @Test
    void descartarCapturaRecusaBdoRecebidoPorEmail() {
        Bdo bdo = bdo(BdoOrigem.EMAIL_AUTOMATICO, StatusConferencia.PENDENTE, null);
        when(bdoRepository.findById(bdo.getId())).thenReturn(Optional.of(bdo));

        assertThatThrownBy(() -> bdoService.descartarCaptura(bdo.getId())).isInstanceOf(ConflitoException.class);
        verify(bdoRepository, never()).delete(any());
    }

    /** Falha ao apagar o arquivo nao desfaz a exclusao do registro (fica so um aviso no log). */
    @Test
    void descartarCapturaSegueQuandoImagemNaoPodeSerRemovida() throws IOException {
        Bdo bdo = bdo(BdoOrigem.FOTO_MANUAL, StatusConferencia.PENDENTE, "tenant/foto.png");
        when(bdoRepository.findById(bdo.getId())).thenReturn(Optional.of(bdo));
        org.mockito.Mockito.doThrow(new IOException("disco")).when(armazenamentoImagemService).remover("tenant/foto.png");

        bdoService.descartarCaptura(bdo.getId());

        verify(bdoRepository).delete(bdo);
    }

    /** HU16: o uso do plano conta so BDO confirmado. */
    @Test
    void contarNoMesCorrenteContaSoBdoConfirmado() {
        when(bdoRepository.countByStatusConferenciaAndDataCapturaBetween(eq(StatusConferencia.CONFIRMADO), any(), any())).thenReturn(7L);

        assertThat(bdoService.contarNoMesCorrente()).isEqualTo(7L);
    }

    private static Bdo bdo(BdoOrigem origem, StatusConferencia status, String imagem) {
        Bdo bdo = new Bdo();
        bdo.setId(UUID.randomUUID());
        bdo.setOrigem(origem);
        bdo.setStatusConferencia(status);
        bdo.setImagemOriginal(imagem);
        return bdo;
    }
}
