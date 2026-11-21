package com.marillia.milebag.business.basebdo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.marillia.milebag.business.basebdo.model.TrocaBaseBdo;
import com.marillia.milebag.business.basebdo.repository.TrocaBaseBdoRepository;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoMudouDeBase;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.business.configuracao.service.BaseAeroportuariaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** V52 (AG): a empresa troca a base de um BDO ainda nao assumido, sempre com motivo. */
@ExtendWith(MockitoExtension.class)
class TrocaBaseBdoServiceTest {

    @Mock
    private TrocaBaseBdoRepository repository;
    @Mock
    private BdoService bdoService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;
    @Mock
    private BaseAeroportuariaService baseAeroportuariaService;
    @Mock
    private ApplicationEventPublisher eventos;

    private TrocaBaseBdoService servico;
    private final UUID bdoId = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final BaseAeroportuaria bsb = new BaseAeroportuaria("BSB", null);
    private final Bdo bdo = new Bdo();

    @BeforeEach
    void setUp() {
        servico = new TrocaBaseBdoService(repository, bdoService, entregaService, bagagemCaronaService, baseAeroportuariaService, eventos);
        bsb.setId(UUID.randomUUID());
        bdo.setId(bdoId);
        bdo.setNumero("GYNLA10001");
        bdo.setBaseId(UUID.randomUUID());
        bdo.setStatusConferencia(com.marillia.milebag.business.bdo.model.StatusConferencia.CONFIRMADO);
        org.mockito.Mockito.lenient().when(baseAeroportuariaService.buscar(bsb.getId())).thenReturn(Optional.of(bsb));
        org.mockito.Mockito.lenient().when(bdoService.travar(bdoId)).thenReturn(Optional.of(bdo));
    }

    @Test
    void trocaComMotivoRegistraEAvisaABaseNova() {
        when(entregaService.buscarPorBdo(bdoId)).thenReturn(Optional.empty());
        when(bagagemCaronaService.entregaPrincipalDe(bdoId)).thenReturn(Optional.empty());
        when(repository.save(any(TrocaBaseBdo.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID anterior = bdo.getBaseId();

        TrocaBaseBdo troca = servico.trocar(bdoId, bsb.getId(), "aeroporto errado no texto", admin);

        verify(bdoService).trocarBase(bdoId, bsb.getId());
        assertThat(troca.getBaseAnteriorId()).isEqualTo(anterior);
        assertThat(troca.getMotivo()).isEqualTo("aeroporto errado no texto");
        ArgumentCaptor<BdoMudouDeBase> evento = ArgumentCaptor.forClass(BdoMudouDeBase.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().numero()).isEqualTo("GYNLA10001");
    }

    @Test
    void semMotivoERecusada() {
        assertThatThrownBy(() -> servico.trocar(bdoId, bsb.getId(), "  ", admin))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Informe o motivo da troca de base.");
        verify(bdoService, never()).trocarBase(any(), any());
    }

    @Test
    void bdoJaAssumidoNaoTrocaDeBase() {
        Entrega assumida = new Entrega();
        assumida.setStatus(EntregaStatus.ATRIBUIDA);
        assumida.setMotoristaResponsavel(UUID.randomUUID());
        when(entregaService.buscarPorBdo(bdoId)).thenReturn(Optional.of(assumida));

        assertThatThrownBy(() -> servico.trocar(bdoId, bsb.getId(), "motivo", admin))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("So da para trocar a base de um BDO que ninguem assumiu.");
        verify(bdoService, never()).trocarBase(any(), any());
    }
}
