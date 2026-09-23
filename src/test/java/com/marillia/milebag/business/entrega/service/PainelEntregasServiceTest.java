package com.marillia.milebag.business.entrega.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.mapper.PainelEntregasMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.ItemPainelDetalheResponse;
import com.marillia.milebag.business.entrega.model.PainelEntregasResponse;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/** Cobre HU19 (US1): painel de entregas pendentes e detalhe de uma entrega. */
@ExtendWith(MockitoExtension.class)
class PainelEntregasServiceTest {

    @Mock
    private EntregaRepository entregaRepository;
    @Mock
    private BdoService bdoService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;

    private PainelEntregasService painelEntregasService;

    @BeforeEach
    void setUp() {
        // PainelEntregasMapper real (nao mock): so mapeia campos, sem
        // dependencias - um mock devolveria null em todo metodo.
        painelEntregasService = new PainelEntregasService(entregaRepository, bdoService, bagagemCaronaService, new PainelEntregasMapper());
    }

    private Entrega entrega(UUID id, UUID bdoId, UUID motoristaId, EntregaStatus status) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setBdoId(bdoId);
        entrega.setMotoristaResponsavel(motoristaId);
        entrega.setStatus(status);
        return entrega;
    }

    private Bdo bdo(UUID id) {
        Bdo bdo = new Bdo();
        bdo.setId(id);
        bdo.setNumero("BDO-123");
        bdo.setPassageiroNome("Fulano de Tal");
        bdo.setEnderecoEntrega("Rua Teste, 123");
        bdo.setTelefonePassageiro("62999990000");
        bdo.setCompanhiaAerea("Companhia X");
        return bdo;
    }

    @Test
    void consultarPainelListaPendentesEContaCorretamente() {
        UUID motoristaId = UUID.randomUUID();
        UUID entregaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        Entrega entrega = entrega(entregaId, bdoId, motoristaId, EntregaStatus.EM_ANDAMENTO);

        when(entregaRepository.findByMotoristaResponsavelAndStatusInOrderByAtualizadoEmAsc(eq(motoristaId), any()))
                .thenReturn(List.of(entrega));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo(bdoId)));
        when(bagagemCaronaService.listarPorEntrega(entregaId)).thenReturn(List.of());

        PainelEntregasResponse resposta = painelEntregasService.consultarPainel(motoristaId);

        assertThat(resposta.contadorPendentes()).isEqualTo(1);
        assertThat(resposta.entregas()).hasSize(1);
        var item = resposta.entregas().get(0);
        assertThat(item.entregaId()).isEqualTo(entregaId);
        assertThat(item.bdoNumero()).isEqualTo("BDO-123");
        assertThat(item.temTrajetoIniciado()).isTrue();
        assertThat(item.marcoMaisRecente()).isNull();
    }

    @Test
    void consultarPainelVazioNaoQuebra() {
        UUID motoristaId = UUID.randomUUID();
        when(entregaRepository.findByMotoristaResponsavelAndStatusInOrderByAtualizadoEmAsc(eq(motoristaId), any()))
                .thenReturn(List.of());

        PainelEntregasResponse resposta = painelEntregasService.consultarPainel(motoristaId);

        assertThat(resposta.contadorPendentes()).isZero();
        assertThat(resposta.entregas()).isEmpty();
    }

    @Test
    void consultarDetalheRetornaCamposCompletosIncluindoTelefone() {
        UUID motoristaId = UUID.randomUUID();
        UUID entregaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        Entrega entrega = entrega(entregaId, bdoId, motoristaId, EntregaStatus.ATRIBUIDA);

        when(entregaRepository.findById(entregaId)).thenReturn(Optional.of(entrega));
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo(bdoId)));
        when(bagagemCaronaService.listarPorEntrega(entregaId)).thenReturn(List.of());

        ItemPainelDetalheResponse detalhe = painelEntregasService.consultarDetalhe(entregaId, motoristaId);

        assertThat(detalhe.telefone()).isEqualTo("62999990000");
        assertThat(detalhe.companhiaAerea()).isEqualTo("Companhia X");
    }

    @Test
    void consultarDetalheLancaAcessoNegadoQuandoEntregaDeOutroMotorista() {
        UUID donoReal = UUID.randomUUID();
        UUID outroMotorista = UUID.randomUUID();
        UUID entregaId = UUID.randomUUID();
        Entrega entrega = entrega(entregaId, UUID.randomUUID(), donoReal, EntregaStatus.ATRIBUIDA);
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.of(entrega));

        assertThatThrownBy(() -> painelEntregasService.consultarDetalhe(entregaId, outroMotorista))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void consultarDetalheLancaNaoEncontradoQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaRepository.findById(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> painelEntregasService.consultarDetalhe(entregaId, UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
