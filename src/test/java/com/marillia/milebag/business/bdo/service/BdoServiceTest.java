package com.marillia.milebag.business.bdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.mapper.BdoMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
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
