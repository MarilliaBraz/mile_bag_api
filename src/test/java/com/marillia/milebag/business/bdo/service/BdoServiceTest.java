package com.marillia.milebag.business.bdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.mapper.BdoMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
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
}
