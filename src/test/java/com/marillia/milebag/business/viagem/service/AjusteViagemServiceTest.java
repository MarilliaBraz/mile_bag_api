package com.marillia.milebag.business.viagem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.tarifa.model.EscopoTarifa;
import com.marillia.milebag.business.tarifa.model.TabelaTarifa;
import com.marillia.milebag.business.tarifa.service.TabelaTarifaService;
import com.marillia.milebag.business.viagem.model.AjusteViagem;
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.repository.AjusteViagemRepository;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Cobre HU17: resolucao do valor vigente (com/sem ajuste) e registro de um novo AjusteViagem. */
@ExtendWith(MockitoExtension.class)
class AjusteViagemServiceTest {

    @Mock
    private AjusteViagemRepository ajusteViagemRepository;
    @Mock
    private TrajetoRepository trajetoRepository;
    @Mock
    private EntregaService entregaService;
    @Mock
    private TabelaTarifaService tabelaTarifaService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;

    private AjusteViagemService ajusteViagemService;

    @BeforeEach
    void setUp() {
        ajusteViagemService = new AjusteViagemService(
                ajusteViagemRepository, trajetoRepository, entregaService, tabelaTarifaService, bagagemCaronaService, new ObjectMapper());
    }

    private Trajeto trajetoConsolidado(UUID entregaId, String kmIda, String kmVolta, String pracasPedagioFinal) {
        Trajeto trajeto = new Trajeto(entregaId, Instant.now());
        trajeto.consolidar(new BigDecimal(kmIda), new BigDecimal(kmVolta), pracasPedagioFinal, 600L, null);
        return trajeto;
    }

    private Entrega entregaDe(UUID id, UUID motoristaId) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setMotoristaResponsavel(motoristaId);
        entrega.setStatus(EntregaStatus.ENCERRADA_COM_SUCESSO);
        return entrega;
    }

    @Test
    void resolverValorVigenteUsaAjusteMaisRecenteQuandoExiste() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        AjusteViagem ajuste = new AjusteViagem(trajeto.getId(), CampoAjusteViagem.KM_IDA, "10.000", "15.000", "motorista errou o km", motoristaId);
        when(ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(trajeto.getId(), CampoAjusteViagem.KM_IDA))
                .thenReturn(Optional.of(ajuste));

        Optional<BigDecimal> valor = ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.KM_IDA);

        assertThat(valor).isPresent();
        assertThat(valor.get()).isEqualByComparingTo("15.000");
    }

    @Test
    void resolverValorVigenteUsaKmFinalDoTrajetoQuandoSemAjuste() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "12.500", "13.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(any(), any())).thenReturn(Optional.empty());

        assertThat(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.KM_VOLTA).get()).isEqualByComparingTo("13.000");
    }

    @Test
    void resolverValorVigenteCalculaTarifaAplicadaOriginalQuandoSemAjuste() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(any(), any())).thenReturn(Optional.empty());
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId)));
        when(bagagemCaronaService.listarPorEntrega(entregaId)).thenReturn(List.of());
        TabelaTarifa tarifa = new TabelaTarifa(
                EscopoTarifa.MOTORISTA, motoristaId, null, new BigDecimal("50.00"), new BigDecimal("2.00"), java.time.LocalDate.now());
        when(tabelaTarifaService.resolverTarifaAplicavel(motoristaId, null)).thenReturn(Optional.of(tarifa));

        Optional<BigDecimal> valor = ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA);

        // 50 fixo + 2 * 20km (10 ida + 10 volta) = 90
        assertThat(valor).isPresent();
        assertThat(valor.get()).isEqualByComparingTo("90.00");
    }

    @Test
    void resolverValorVigenteVazioQuandoTarifaNaoConfigurada() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(any(), any())).thenReturn(Optional.empty());
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId)));
        when(bagagemCaronaService.listarPorEntrega(entregaId)).thenReturn(List.of());
        when(tabelaTarifaService.resolverTarifaAplicavel(motoristaId, null)).thenReturn(Optional.empty());

        assertThat(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA)).isEmpty();
    }

    @Test
    void resolverValorVigentePedagioZeroQuandoSemTrechoSinalizado() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(any(), any())).thenReturn(Optional.empty());

        assertThat(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO).get()).isEqualByComparingTo("0");
    }

    @Test
    void resolverValorVigentePedagioVazioQuandoTrechoSinalizadoSemValorCadastrado() {
        UUID entregaId = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[{\"descricao\":\"pedagio\",\"valorCadastrado\":false}]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(any(), any())).thenReturn(Optional.empty());

        assertThat(ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO)).isEmpty();
    }

    @Test
    void registrarAjusteRecusaJustificativaVazia() {
        assertThatThrownBy(() -> ajusteViagemService.registrarAjuste(UUID.randomUUID(), UUID.randomUUID(), CampoAjusteViagem.KM_IDA, "15", " "))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void registrarAjusteRecusaTrajetoInexistente() {
        UUID viagemId = UUID.randomUUID();
        when(trajetoRepository.findById(viagemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ajusteViagemService.registrarAjuste(viagemId, UUID.randomUUID(), CampoAjusteViagem.KM_IDA, "15", "justificativa"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void registrarAjusteRecusaQuandoTrajetoNaoPertenceAoMotorista() {
        UUID entregaId = UUID.randomUUID();
        UUID donoDaEntrega = UUID.randomUUID();
        UUID outroMotorista = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, donoDaEntrega)));

        assertThatThrownBy(() -> ajusteViagemService.registrarAjuste(trajeto.getId(), outroMotorista, CampoAjusteViagem.KM_IDA, "15", "justificativa"))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void registrarAjusteRecusaQuandoTrajetoAindaNaoConsolidado() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Trajeto trajeto = new Trajeto(entregaId, Instant.now());
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId)));

        assertThatThrownBy(() -> ajusteViagemService.registrarAjuste(trajeto.getId(), motoristaId, CampoAjusteViagem.KM_IDA, "15", "justificativa"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void registrarAjusteResolveValorAnteriorESalva() {
        UUID entregaId = UUID.randomUUID();
        UUID motoristaId = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, motoristaId)));
        when(ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(any(), any())).thenReturn(Optional.empty());
        when(ajusteViagemRepository.save(any(AjusteViagem.class))).thenAnswer(inv -> inv.getArgument(0));

        AjusteViagem ajuste = ajusteViagemService.registrarAjuste(trajeto.getId(), motoristaId, CampoAjusteViagem.KM_IDA, "15.000", "km real era maior");

        ArgumentCaptor<AjusteViagem> captor = ArgumentCaptor.forClass(AjusteViagem.class);
        org.mockito.Mockito.verify(ajusteViagemRepository).save(captor.capture());
        assertThat(captor.getValue().getValorAnterior()).isEqualTo("10.000");
        assertThat(captor.getValue().getValorNovo()).isEqualTo("15.000");
        assertThat(ajuste.getJustificativa()).isEqualTo("km real era maior");
    }

    @Test
    void listarPorViagemRecusaQuandoNaoPertenceAoMotorista() {
        UUID entregaId = UUID.randomUUID();
        UUID donoDaEntrega = UUID.randomUUID();
        Trajeto trajeto = trajetoConsolidado(entregaId, "10.000", "10.000", "[]");
        when(trajetoRepository.findById(trajeto.getId())).thenReturn(Optional.of(trajeto));
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaDe(entregaId, donoDaEntrega)));

        assertThatThrownBy(() -> ajusteViagemService.listarPorViagem(trajeto.getId(), UUID.randomUUID()))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void temAjusteRefleteExistenciaDeHistorico() {
        UUID viagemId = UUID.randomUUID();
        when(ajusteViagemRepository.findByViagemIdOrderByCriadoEmDesc(viagemId)).thenReturn(List.of());
        assertThat(ajusteViagemService.temAjuste(viagemId)).isFalse();
    }
}
