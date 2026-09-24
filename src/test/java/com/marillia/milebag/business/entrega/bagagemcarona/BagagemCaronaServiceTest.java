package com.marillia.milebag.business.entrega.bagagemcarona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.geo.GeocodingClient;
import com.marillia.milebag.core.geo.RoutingClient;

/**
 * Cobre o refactor de acoplamento em BagagemCaronaService: consulta Bdo via
 * BdoService (antes BdoRepository direto) e usa GeocodingClient/RoutingClient
 * de core.geo (antes vinham de business.rota, criando um ciclo
 * entrega<->rota).
 */
@ExtendWith(MockitoExtension.class)
class BagagemCaronaServiceTest {

    @Mock
    private BagagemCaronaRepository bagagemCaronaRepository;
    @Mock
    private EntregaRepository entregaRepository;
    @Mock
    private BdoService bdoService;
    @Mock
    private GeocodingClient geocodingClient;
    @Mock
    private RoutingClient routingClient;

    @InjectMocks
    private BagagemCaronaService bagagemCaronaService;

    private Entrega entregaComStatus(UUID id, UUID bdoId, EntregaStatus status) {
        Entrega entrega = new Entrega();
        entrega.setId(id);
        entrega.setBdoId(bdoId);
        entrega.setStatus(status);
        return entrega;
    }

    @Test
    void vincularConsultaBdosViaBdoServiceEUsaGeocodingDeCoreGeo() {
        UUID entregaPrincipalId = UUID.randomUUID();
        UUID bdoPrincipalId = UUID.randomUUID();
        UUID bdoCaronaId = UUID.randomUUID();

        when(entregaRepository.findById(entregaPrincipalId))
                .thenReturn(Optional.of(entregaComStatus(entregaPrincipalId, bdoPrincipalId, EntregaStatus.EM_ANDAMENTO)));
        Bdo bdoCarona = new Bdo();
        bdoCarona.setEnderecoEntrega("Rua A, 1");
        Bdo bdoPrincipal = new Bdo();
        bdoPrincipal.setEnderecoEntrega("Rua B, 2");
        when(bdoService.buscarPorId(bdoCaronaId)).thenReturn(Optional.of(bdoCarona));
        when(bdoService.buscarPorId(bdoPrincipalId)).thenReturn(Optional.of(bdoPrincipal));
        when(bagagemCaronaRepository.findByBdoId(bdoCaronaId)).thenReturn(Optional.empty());
        when(geocodingClient.geocodificar(any())).thenReturn(Optional.empty());
        when(bagagemCaronaRepository.save(any(BagagemCarona.class))).thenAnswer(inv -> inv.getArgument(0));

        BagagemCarona resultado = bagagemCaronaService.vincular(entregaPrincipalId, bdoCaronaId);

        // Sem geocodificacao disponivel, a quilometragem complementar cai
        // para 0 (best-effort, nao bloqueia a vinculacao).
        assertThat(resultado.getQuilometragemComplementar()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void vincularLancaQuandoEntregaPrincipalJaEncerrada() {
        UUID entregaPrincipalId = UUID.randomUUID();
        when(entregaRepository.findById(entregaPrincipalId)).thenReturn(
                Optional.of(entregaComStatus(entregaPrincipalId, UUID.randomUUID(), EntregaStatus.ENCERRADA_COM_SUCESSO)));

        assertThatThrownBy(() -> bagagemCaronaService.vincular(entregaPrincipalId, UUID.randomUUID()))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void vincularLancaQuandoBdoDaCaronaNaoExiste() {
        UUID entregaPrincipalId = UUID.randomUUID();
        UUID bdoCaronaId = UUID.randomUUID();
        when(entregaRepository.findById(entregaPrincipalId)).thenReturn(
                Optional.of(entregaComStatus(entregaPrincipalId, UUID.randomUUID(), EntregaStatus.EM_ANDAMENTO)));
        when(bdoService.buscarPorId(bdoCaronaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bagagemCaronaService.vincular(entregaPrincipalId, bdoCaronaId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void vincularRecusaBdoQueJaTemEntregaPropria() {
        UUID entregaPrincipalId = UUID.randomUUID();
        UUID bdoCaronaId = UUID.randomUUID();
        when(entregaRepository.findById(entregaPrincipalId)).thenReturn(
                Optional.of(entregaComStatus(entregaPrincipalId, UUID.randomUUID(), EntregaStatus.EM_ANDAMENTO)));
        when(bdoService.buscarPorId(bdoCaronaId)).thenReturn(Optional.of(new Bdo()));
        when(entregaRepository.findByBdoId(bdoCaronaId))
                .thenReturn(Optional.of(entregaComStatus(UUID.randomUUID(), bdoCaronaId, EntregaStatus.ATRIBUIDA)));

        assertThatThrownBy(() -> bagagemCaronaService.vincular(entregaPrincipalId, bdoCaronaId)).isInstanceOf(ConflitoException.class);
        verify(bagagemCaronaRepository, never()).save(any());
    }
}
