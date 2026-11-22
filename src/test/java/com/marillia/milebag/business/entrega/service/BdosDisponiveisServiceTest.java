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

    // Item 8: sugestao de bagagem carona pelo mesmo endereco.

    private static Bdo bdoEm(String endereco, String numero, Integer malas) {
        Bdo bdo = new Bdo();
        bdo.setId(UUID.randomUUID());
        bdo.setBaseId(BASE);
        bdo.setNumero(numero);
        bdo.setEnderecoEntrega(endereco);
        bdo.setQuantidadeMalas(malas);
        bdo.setDataCaptura(Instant.now());
        return bdo;
    }

    private Entrega entregaDoMotorista(Bdo bdo, EntregaStatus status) {
        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setBdoId(bdo.getId());
        entrega.setMotoristaResponsavel(MOTORISTA);
        entrega.setStatus(status);
        when(entregaRepository.findById(entrega.getId())).thenReturn(Optional.of(entrega));
        org.mockito.Mockito.lenient().when(bdoRepository.findById(bdo.getId())).thenReturn(Optional.of(bdo));
        return entrega;
    }

    @Test
    void sugereComoCaronaSoOsBdosDoMesmoEndereco() {
        Bdo principal = bdoEm("Av. Goiás, 100 - Centro", "GYNG100001", 2);
        Entrega entrega = entregaDoMotorista(principal, EntregaStatus.ATRIBUIDA);
        Bdo mesmoLugar = bdoEm("AVENIDA GOIAS 100, apto 12", "GYNG100002", 1);
        Bdo outroLugar = bdoEm("Av. Goiás, 102", "GYNG100003", 1);
        when(bdoRepository.findDisponiveisParaAutoatribuicao()).thenReturn(List.of(mesmoLugar, outroLugar));

        var sugestoes = bdosDisponiveisService.sugestoesDeCarona(entrega.getId(), MOTORISTA);

        assertThat(sugestoes).extracting("bdoNumero").containsExactly("GYNG100002");
        assertThat(sugestoes.get(0).quantidadeMalas()).isEqualTo(1);
    }

    @Test
    void naoSugereBdoDeOutraBase() {
        Bdo principal = bdoEm("Rua 10, 250", "GYNG200001", 1);
        Entrega entrega = entregaDoMotorista(principal, EntregaStatus.EM_ANDAMENTO);
        Bdo outraBase = bdoEm("Rua 10, 250", "BSBG200002", 1);
        outraBase.setBaseId(UUID.randomUUID());
        when(bdoRepository.findDisponiveisParaAutoatribuicao()).thenReturn(List.of(outraBase));

        assertThat(bdosDisponiveisService.sugestoesDeCarona(entrega.getId(), MOTORISTA)).isEmpty();
    }

    @Test
    void entregaFinalizadaNaoTemSugestao() {
        Entrega entrega = entregaDoMotorista(bdoEm("Rua 10, 250", "GYNG300001", 1), EntregaStatus.ENCERRADA_COM_SUCESSO);

        assertThat(bdosDisponiveisService.sugestoesDeCarona(entrega.getId(), MOTORISTA)).isEmpty();
        org.mockito.Mockito.verify(bdoRepository, org.mockito.Mockito.never()).findDisponiveisParaAutoatribuicao();
    }

    @Test
    void entregaDeOutroMotoristaERecusada() {
        Entrega entrega = entregaDoMotorista(bdoEm("Rua 10, 250", "GYNG400001", 1), EntregaStatus.ATRIBUIDA);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bdosDisponiveisService.sugestoesDeCarona(entrega.getId(), UUID.randomUUID()))
                .isInstanceOf(com.marillia.milebag.core.exception.AcessoNegadoException.class);
    }
}
