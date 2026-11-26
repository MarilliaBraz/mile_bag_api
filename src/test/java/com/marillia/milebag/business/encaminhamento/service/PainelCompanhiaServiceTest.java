package com.marillia.milebag.business.encaminhamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaResponse;
import com.marillia.milebag.business.encaminhamento.repository.EncaminhamentoEntregaRepository;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.config.security.Papeis;

/** Painel da companhia: o administrador ve os rascunhos de todos; o funcionario, so os seus. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PainelCompanhiaServiceTest {

    @Mock
    private BagagemEncontradaRepository bagagemRepository;
    @Mock
    private EncaminhamentoEntregaRepository encaminhamentoRepository;
    @InjectMocks
    private PainelCompanhiaService servico;

    private final UUID companhiaId = UUID.randomUUID();
    private final UUID funcionarioId = UUID.randomUUID();

    @Test
    void contaRascunhosConformeOPapelEOAndamentoDasEncaminhadas() {
        when(bagagemRepository.countBySituacao(SituacaoBagagemEncontrada.RASCUNHO)).thenReturn(7L);
        when(bagagemRepository.countByCriadoPorAndSituacao(funcionarioId, SituacaoBagagemEncontrada.RASCUNHO)).thenReturn(2L);
        when(encaminhamentoRepository.contarRegistradasSemEncaminhamento(companhiaId)).thenReturn(4L);
        when(encaminhamentoRepository.countByCompanhiaIdAndEtapaIn(eq(companhiaId), any())).thenReturn(0L);
        when(encaminhamentoRepository.countByCompanhiaIdAndEtapaIn(companhiaId, PainelCompanhiaService.EM_ANDAMENTO)).thenReturn(3L);
        when(encaminhamentoRepository.countByCompanhiaIdAndEtapaIn(companhiaId, EnumSet.of(EtapaEntrega.NAO_ENTREGUE))).thenReturn(1L);

        PainelCompanhiaResponse doAdmin = servico.painel(new AuthenticatedUser(funcionarioId, companhiaId, Papeis.ADMINISTRADOR_COMPANHIA));
        PainelCompanhiaResponse doFuncionario = servico.painel(new AuthenticatedUser(funcionarioId, companhiaId, Papeis.FUNCIONARIO_COMPANHIA));

        assertThat(doAdmin).isEqualTo(new PainelCompanhiaResponse(7, 4, 3, 1));
        assertThat(doFuncionario.rascunhos()).isEqualTo(2);
    }
}
