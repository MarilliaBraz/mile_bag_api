package com.marillia.milebag.business.acompanhamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.acompanhamento.model.AcompanhamentoResponse;
import com.marillia.milebag.business.acompanhamento.model.EtapaAcompanhamento;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.configuracao.service.IdentidadeVisualTenantService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.model.JanelaReagendamento;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.rota.service.RotaService;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.service.TenantService;
import com.marillia.milebag.business.viagem.model.LinkAcompanhamento;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.service.AcompanhamentoViagem;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.geo.Coordenada;

@ExtendWith(MockitoExtension.class)
class AcompanhamentoServiceTest {

    @Mock
    private AcompanhamentoViagem acompanhamentoViagem;
    @Mock
    private ViagemService viagemService;
    @Mock
    private EntregaService entregaService;
    @Mock
    private BdoService bdoService;
    @Mock
    private RotaService rotaService;
    @Mock
    private TenantService tenantService;
    @Mock
    private IdentidadeVisualTenantService identidadeVisualTenantService;

    private AcompanhamentoService service;
    private Trajeto trajeto;
    private Entrega entrega;
    private LinkAcompanhamento link;

    @BeforeEach
    void setUp() {
        service = new AcompanhamentoService(
                acompanhamentoViagem, viagemService, entregaService, bdoService, rotaService, tenantService, identidadeVisualTenantService);
        UUID tenantId = UUID.randomUUID();
        entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(EntregaStatus.EM_ANDAMENTO);
        trajeto = new Trajeto(entrega.getId(), UUID.randomUUID(), Instant.parse("2026-10-28T13:00:00Z"));
        trajeto.setId(UUID.randomUUID());
        link = new LinkAcompanhamento(tenantId, trajeto);

        Bdo bdo = new Bdo();
        bdo.setNumero("GYNLA12342");
        lenient().when(viagemService.buscarTrajetoPorId(trajeto.getId())).thenReturn(Optional.of(trajeto));
        lenient().when(entregaService.buscarPorId(entrega.getId())).thenReturn(Optional.of(entrega));
        lenient().when(bdoService.buscarPorId(entrega.getBdoId())).thenReturn(Optional.of(bdo));
        lenient().when(tenantService.buscar(tenantId)).thenReturn(new Tenant("transportes-alfa", "Transportes Alfa"));
        lenient().when(identidadeVisualTenantService.buscar()).thenReturn(Optional.empty());
        lenient().when(rotaService.buscarDestino(entrega.getId())).thenReturn(Optional.of(new Coordenada(-16.70, -49.27)));
        lenient().when(viagemService.marcoMaisRecenteDoTrajeto(any())).thenReturn(Optional.empty());
    }

    @Test
    void viagemEmAndamentoMostraPosicaoEtapaEDestino() {
        trajeto.atualizarPosicao(-16.68, -49.26, Instant.parse("2026-10-28T13:10:00Z"));
        when(viagemService.marcoMaisRecenteDoTrajeto(trajeto.getId())).thenReturn(Optional.of("A_CAMINHO"));

        AcompanhamentoResponse resposta = service.consultar(link);

        assertThat(resposta.etapa()).isEqualTo(EtapaAcompanhamento.A_CAMINHO);
        assertThat(resposta.bdoNumero()).isEqualTo("GYNLA12342");
        assertThat(resposta.empresa().nome()).isEqualTo("Transportes Alfa");
        assertThat(resposta.empresa().logotipoUrl()).isNull();
        assertThat(resposta.posicao().latitude()).isEqualTo(-16.68);
        assertThat(resposta.destino().longitude()).isEqualTo(-49.27);
        assertThat(resposta.iniciadaEm()).isEqualTo(Instant.parse("2026-10-28T13:00:00Z"));
    }

    @Test
    void semMarcoNemPosicaoAindaEViagemIniciada() {
        AcompanhamentoResponse resposta = service.consultar(link);

        assertThat(resposta.etapa()).isEqualTo(EtapaAcompanhamento.VIAGEM_INICIADA);
        assertThat(resposta.posicao()).isNull();
    }

    /** RN16: entregue, a pagina nao mostra mais a posicao, mesmo que ela ainda estivesse gravada. */
    @Test
    void entregueNaoMostraPosicao() {
        trajeto.atualizarPosicao(-16.68, -49.26, Instant.now());
        entrega.setStatus(EntregaStatus.ENCERRADA_COM_SUCESSO);

        AcompanhamentoResponse resposta = service.consultar(link);

        assertThat(resposta.etapa()).isEqualTo(EtapaAcompanhamento.ENTREGUE);
        assertThat(resposta.posicao()).isNull();
    }

    @Test
    void ocorrenciaMostraNaoEntregueComAJanelaDeReagendamento() {
        trajeto.consolidar(BigDecimal.ONE, BigDecimal.ONE, "[]", 60, null);
        trajeto.vincularOcorrencia(UUID.randomUUID(), Instant.now());
        entrega.setStatus(EntregaStatus.REABERTA);
        entrega.definirJanelaReagendamento(
                new JanelaReagendamento(Instant.parse("2026-10-29T17:00:00Z"), Instant.parse("2026-10-29T19:00:00Z")));

        AcompanhamentoResponse resposta = service.consultar(link);

        assertThat(resposta.etapa()).isEqualTo(EtapaAcompanhamento.NAO_ENTREGUE);
        assertThat(resposta.posicao()).isNull();
        assertThat(resposta.janelaReagendamento().inicio()).isEqualTo(Instant.parse("2026-10-29T17:00:00Z"));
    }

    /** Uma tentativa sem sucesso continua "nao entregue" no link dela, mesmo depois de outra tentativa entregar. */
    @Test
    void linkDeTentativaAnteriorContinuaNaoEntregue() {
        trajeto.vincularOcorrencia(UUID.randomUUID(), Instant.now());
        entrega.setStatus(EntregaStatus.ENCERRADA_COM_SUCESSO);

        assertThat(service.consultar(link).etapa()).isEqualTo(EtapaAcompanhamento.NAO_ENTREGUE);
    }

    @Test
    void codigoInexistenteE404() {
        UUID codigo = UUID.randomUUID();
        when(acompanhamentoViagem.buscarLink(codigo)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(codigo))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Link de acompanhamento invalido.");
    }
}
