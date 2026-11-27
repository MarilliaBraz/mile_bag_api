package com.marillia.milebag.business.acompanhamento.service;

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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.marillia.milebag.business.acompanhamento.model.AvaliarEntregaRequest;
import com.marillia.milebag.business.acompanhamento.repository.AvaliacaoEntregaRepository;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.viagem.model.LinkAcompanhamento;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** V65: a pesquisa so abre com a entrega concluida, uma vez por entrega, e com nota de 0 a 10. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvaliacaoEntregaServiceTest {

    @Mock
    private AvaliacaoEntregaRepository repository;
    @Mock
    private EntregaService entregaService;
    @InjectMocks
    private AvaliacaoEntregaService servico;

    @Test
    void recusaAntesDaEntregaRepetidaENotaInvalida() {
        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(EntregaStatus.EM_ANDAMENTO);
        Trajeto trajeto = new Trajeto(entrega.getId(), UUID.randomUUID(), Instant.now());
        trajeto.setId(UUID.randomUUID());
        LinkAcompanhamento link = new LinkAcompanhamento(UUID.randomUUID(), trajeto);
        when(entregaService.buscarPorId(entrega.getId())).thenReturn(Optional.of(entrega));

        assertThatThrownBy(() -> servico.registrar(link, new AvaliarEntregaRequest(10, 5, true, null))).isInstanceOf(ConflitoException.class);

        entrega.setStatus(EntregaStatus.ENCERRADA_COM_SUCESSO);
        assertThatThrownBy(() -> servico.registrar(link, new AvaliarEntregaRequest(11, null, null, null))).isInstanceOf(RegraNegocioException.class);

        when(repository.existsByEntregaId(entrega.getId())).thenReturn(true);
        assertThatThrownBy(() -> servico.registrar(link, new AvaliarEntregaRequest(9, null, null, null))).isInstanceOf(ConflitoException.class);
        verify(repository, never()).save(any());
    }
}
