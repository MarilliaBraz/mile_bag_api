package com.marillia.milebag.business.correcao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.correcao.model.CorrecaoEntrega;
import com.marillia.milebag.business.correcao.model.CorrecaoInformada;
import com.marillia.milebag.business.correcao.model.EtapaCorrecao;
import com.marillia.milebag.business.correcao.model.MomentoCorrecao;
import com.marillia.milebag.business.correcao.repository.CorrecaoEntregaRepository;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class CorrecaoEntregaServiceTest {

    @Mock
    private CorrecaoEntregaRepository repository;
    @Mock
    private MotivoOcorrenciaService motivoOcorrenciaService;

    @InjectMocks
    private CorrecaoEntregaService servico;

    private final UUID entrega = UUID.randomUUID();
    private final UUID motorista = UUID.randomUUID();

    @Test
    void motivoEObrigatorioEDaListaDeCorrecao() {
        assertThatThrownBy(() -> servico.exigirMotivo(null, null)).hasMessage("Informe o motivo da correcao.");
        UUID motivo = UUID.randomUUID();

        servico.exigirMotivo(motivo, "detalhe");

        verify(motivoOcorrenciaService).buscarAtivoOuFalhar(motivo, FinalidadeMotivo.CORRECAO);
    }

    @Test
    void correcoesDoAparelhoViramRegistrosNoFluxo() {
        when(repository.save(any(CorrecaoEntrega.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant ontem = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant padrao = Instant.now();
        var correcoes = List.of(
                new CorrecaoInformada(EtapaCorrecao.FOTO_PORTA, UUID.randomUUID(), null, "foto anterior", "foto nova", ontem.toString()),
                new CorrecaoInformada(EtapaCorrecao.RECEBEDOR, UUID.randomUUID(), "nome", "Titular - Ana", "Vizinho - Rui",
                        Instant.now().plus(2, ChronoUnit.DAYS).toString()));

        servico.registrarDoFluxo(entrega, motorista, correcoes, padrao);

        ArgumentCaptor<CorrecaoEntrega> salvas = ArgumentCaptor.forClass(CorrecaoEntrega.class);
        verify(repository, times(2)).save(salvas.capture());
        assertThat(salvas.getAllValues()).allMatch(c -> c.getMomento() == MomentoCorrecao.NO_FLUXO && c.getCorrigidoPor().equals(motorista));
        assertThat(salvas.getAllValues().get(0).getDataHora()).isEqualTo(ontem);
        // Relogio do aparelho adiantado: vale a hora do registro.
        assertThat(salvas.getAllValues().get(1).getDataHora()).isEqualTo(padrao);
    }

    /** Auditoria nao bloqueia a entrega: o excesso e cortado e a correcao invalida e descartada, sem erro. */
    @Test
    void correcoesDemaisOuInvalidasNaoImpedemAEntrega() {
        when(repository.save(any(CorrecaoEntrega.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID deOutraLista = UUID.randomUUID();
        when(motivoOcorrenciaService.buscarDaFinalidadeOuFalhar(deOutraLista, FinalidadeMotivo.CORRECAO))
                .thenThrow(new com.marillia.milebag.core.exception.RecursoNaoEncontradoException("Motivo nao encontrado."));
        var demais = new java.util.ArrayList<CorrecaoInformada>(Collections.nCopies(CorrecaoEntregaService.MAXIMO_CORRECOES_NO_FLUXO + 1,
                new CorrecaoInformada(EtapaCorrecao.FOTO_PORTA, UUID.randomUUID(), null, null, null, null)));
        demais.set(0, new CorrecaoInformada(EtapaCorrecao.FOTO_PORTA, deOutraLista, null, null, null, null));
        demais.set(1, new CorrecaoInformada(null, UUID.randomUUID(), null, null, null, null));

        servico.registrarDoFluxo(entrega, motorista, demais, Instant.now());

        verify(repository, times(CorrecaoEntregaService.MAXIMO_CORRECOES_NO_FLUXO - 2)).save(any(CorrecaoEntrega.class));
    }

    @Test
    void semCorrecoesNaoGravaNada() {
        servico.registrarDoFluxo(entrega, motorista, null, Instant.now());
        servico.registrarDoFluxo(entrega, motorista, List.of(), Instant.now());

        verifyNoInteractions(repository, motivoOcorrenciaService);
    }
}
