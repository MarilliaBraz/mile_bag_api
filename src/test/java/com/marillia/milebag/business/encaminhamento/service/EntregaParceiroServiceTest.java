package com.marillia.milebag.business.encaminhamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;
import com.marillia.milebag.business.encaminhamento.model.EntregaParceiroResponse;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.repository.EncaminhamentoEntregaRepository;
import com.marillia.milebag.business.encaminhamento.repository.EntregaParceiroRepository;
import com.marillia.milebag.business.rastreabilidade.model.FotoGestaoResponse;
import com.marillia.milebag.business.rastreabilidade.service.RastreabilidadeEntregaService;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.business.viagem.service.AcompanhamentoViagem;
import com.marillia.milebag.core.config.security.TenantContext;

/** Fase 2: a companhia ve o comprovante, a ultima tentativa e o contato da entrega do parceiro, e volta ao seu tenant. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntregaParceiroServiceTest {

    @Mock
    private EncaminhamentoEntregaRepository encaminhamentoRepository;
    @Mock
    private EntregaParceiroRepository repository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private RastreabilidadeEntregaService rastreabilidade;
    @Mock
    private AcompanhamentoViagem acompanhamento;
    @InjectMocks
    private EntregaParceiroService servico;

    private final UUID companhia = UUID.randomUUID();
    private final UUID empresa = UUID.randomUUID();

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    void trazComprovanteFotosAtuaisEContatoEVoltaAoTenantDaCompanhia() {
        TenantContext.set(companhia);
        UUID bagagem = UUID.randomUUID();
        UUID bdo = UUID.randomUUID();
        UUID entrega = UUID.randomUUID();
        Instant agora = Instant.parse("2026-11-26T18:00:00Z");
        EncaminhamentoEntrega encaminhamento = new EncaminhamentoEntrega(bagagem, companhia, empresa, bdo, "GRUZZ1", "Ana", "Rua A", UUID.randomUUID(), agora);
        when(encaminhamentoRepository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagem, companhia, EtapaEntrega.CANCELADO))
                .thenReturn(Optional.of(encaminhamento));
        when(tenantRepository.findById(empresa)).thenReturn(Optional.empty());
        when(repository.contato(bdo)).thenReturn(List.<Object[]>of(new Object[] {"62999990000", "ana@exemplo.com"}));
        when(repository.entregaDoBdo(bdo)).thenReturn(List.of(entrega));
        when(repository.comprovante(entrega)).thenReturn(List.<Object[]>of(new Object[] {agora, "Ana", "Proprio passageiro", -16.6, -49.2}));
        when(repository.ultimaOcorrencia(entrega)).thenReturn(List.of());
        when(repository.linkAcompanhamento(entrega)).thenReturn(List.of(UUID.randomUUID()));
        when(acompanhamento.url(any())).thenReturn("https://milebag/acompanhar/x");
        UUID fotoAtual = UUID.randomUUID();
        when(rastreabilidade.fotos(entrega)).thenAnswer(chamada -> {
            assertThat(TenantContext.get()).isEqualTo(empresa);
            return List.of(new FotoGestaoResponse(fotoAtual, "PORTA", agora, false), new FotoGestaoResponse(UUID.randomUUID(), "PORTA", agora, true));
        });

        EntregaParceiroResponse resposta = servico.consultar(bagagem);

        assertThat(TenantContext.get()).isEqualTo(companhia);
        assertThat(resposta.comprovante().nomeRecebedor()).isEqualTo("Ana");
        assertThat(resposta.telefonePassageiro()).isEqualTo("62999990000");
        assertThat(resposta.linkAcompanhamento()).isEqualTo("https://milebag/acompanhar/x");
        assertThat(resposta.fotos()).extracting(EntregaParceiroResponse.Foto::id).containsExactly(fotoAtual);
    }
}
