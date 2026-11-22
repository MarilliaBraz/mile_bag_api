package com.marillia.milebag.business.entrega.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.model.PendenciaContatoBdo;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.business.entrega.mapper.PainelEntregasMapper;
import com.marillia.milebag.business.entrega.model.BdosDisponiveisResponse;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;

/** Cobre HU19 (FR-007): lista de BDOs confirmados sem motorista responsavel. */
@ExtendWith(MockitoExtension.class)
class BdosDisponiveisServiceTest {

    @Mock
    private BdoRepository bdoRepository;
    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private com.marillia.milebag.business.tenant.service.UsuarioConsultaService usuarioConsultaService;

    private static final java.util.UUID MOTORISTA = java.util.UUID.randomUUID();
    private static final java.util.UUID BASE = java.util.UUID.randomUUID();

    private BdosDisponiveisService bdosDisponiveisService;

    @BeforeEach
    void setUp() {
        bdosDisponiveisService = new BdosDisponiveisService(bdoRepository, entregaRepository, new PainelEntregasMapper(), usuarioConsultaService);
        org.mockito.Mockito.lenient().when(usuarioConsultaService.baseDoMotorista(MOTORISTA)).thenReturn(java.util.Optional.of(BASE));
    }

    @Test
    void consultarDisponiveisMapeiaOrigemEDadosResumidos() {
        UUID bdoId = UUID.randomUUID();
        Bdo bdo = new Bdo();
        bdo.setId(bdoId);
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setNumero("BDO-999");
        bdo.setPassageiroNome("Ciclana");
        bdo.setEnderecoEntrega("Av. Teste, 456");
        bdo.setDataCaptura(Instant.now());
        when(bdoRepository.findDisponiveisParaAutoatribuicao()).thenReturn(List.of(bdo));

        BdosDisponiveisResponse resposta = bdosDisponiveisService.consultarDisponiveis(MOTORISTA);

        assertThat(resposta.bdos()).hasSize(1);
        var item = resposta.bdos().get(0);
        assertThat(item.bdoId()).isEqualTo(bdoId);
        assertThat(item.origem()).isEqualTo(BdoOrigem.EMAIL_AUTOMATICO);
        assertThat(item.bdoNumero()).isEqualTo("BDO-999");
    }

    @Test
    void consultarDisponiveisSinalizaContatoIncompletoSemTirarDaLista() {
        Bdo bdo = new Bdo();
        bdo.setId(UUID.randomUUID());
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setEnderecoEntrega("Rua Sem Numero, Centro");
        bdo.setDataCaptura(Instant.now());
        when(bdoRepository.findDisponiveisParaAutoatribuicao()).thenReturn(List.of(bdo));

        var item = bdosDisponiveisService.consultarDisponiveis(MOTORISTA).bdos().get(0);

        assertThat(item.pendenciasContato())
                .containsExactly(PendenciaContatoBdo.ENDERECO_SEM_NUMERO, PendenciaContatoBdo.TELEFONE_AUSENTE);
    }

    @Test
    void entregaReabertaPorReagendamentoApareceComAJanelaCombinada() {
        Bdo bdo = new Bdo();
        bdo.setId(UUID.randomUUID());
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setDataCaptura(Instant.now());
        Entrega reaberta = new Entrega();
        reaberta.setBdoId(bdo.getId());
        reaberta.setStatus(EntregaStatus.REABERTA);
        JanelaReagendamento janela =
                new JanelaReagendamento(Instant.parse("2026-10-23T17:00:00Z"), Instant.parse("2026-10-23T19:00:00Z"));
        reaberta.definirJanelaReagendamento(janela);
        when(bdoRepository.findDisponiveisParaAutoatribuicao()).thenReturn(List.of(bdo));
        when(entregaRepository.findByBdoId(bdo.getId())).thenReturn(Optional.of(reaberta));

        var item = bdosDisponiveisService.consultarDisponiveis(MOTORISTA).bdos().get(0);

        assertThat(item.reaberta()).isTrue();
        assertThat(item.janelaReagendamento()).isEqualTo(janela);
    }

    @Test
    void consultarDisponiveisVazioNaoQuebra() {
        when(bdoRepository.findDisponiveisParaAutoatribuicao()).thenReturn(List.of());

        assertThat(bdosDisponiveisService.consultarDisponiveis(MOTORISTA).bdos()).isEmpty();
    }

    /** V52: o motorista so ve os BDOs da base dele; sem base, nenhum (e o aviso para a empresa definir). */
    @Test
    void soOsBdosDaBaseDoMotorista() {
        com.marillia.milebag.business.bdo.model.Bdo daBase = new com.marillia.milebag.business.bdo.model.Bdo();
        daBase.setId(java.util.UUID.randomUUID());
        daBase.setBaseId(BASE);
        daBase.setDataCaptura(java.time.Instant.now());
        com.marillia.milebag.business.bdo.model.Bdo deOutra = new com.marillia.milebag.business.bdo.model.Bdo();
        deOutra.setId(java.util.UUID.randomUUID());
        deOutra.setBaseId(java.util.UUID.randomUUID());
        deOutra.setDataCaptura(java.time.Instant.now());
        org.mockito.Mockito.when(bdoRepository.findDisponiveisParaAutoatribuicao()).thenReturn(java.util.List.of(daBase, deOutra));

        var resposta = bdosDisponiveisService.consultarDisponiveis(MOTORISTA);

        org.assertj.core.api.Assertions.assertThat(resposta.bdos()).extracting("bdoId").containsExactly(daBase.getId());
        org.assertj.core.api.Assertions.assertThat(resposta.semBase()).isFalse();
    }

    @Test
    void motoristaSemBaseNaoVeBdos() {
        java.util.UUID semBase = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(usuarioConsultaService.baseDoMotorista(semBase)).thenReturn(java.util.Optional.empty());

        var resposta = bdosDisponiveisService.consultarDisponiveis(semBase);

        org.assertj.core.api.Assertions.assertThat(resposta.bdos()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(resposta.semBase()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(bdoRepository);
    }
}
