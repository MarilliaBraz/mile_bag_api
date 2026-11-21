package com.marillia.milebag.business.rastreabilidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.cancelamento.model.CancelamentoBdoResponse;
import com.marillia.milebag.business.cancelamento.service.CancelamentoBdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.dispensa.model.DispensaEntregaResponse;
import com.marillia.milebag.business.dispensa.service.DispensaEntregaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.ocorrencia.model.Ocorrencia;
import com.marillia.milebag.business.ocorrencia.service.OcorrenciaService;
import com.marillia.milebag.business.rastreabilidade.model.EventoRastreabilidade;
import com.marillia.milebag.business.rastreabilidade.model.EventoRastreabilidade.TipoEventoRastreabilidade;
import com.marillia.milebag.business.rastreabilidade.model.HistoricoEntregaResponse;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/** A linha do tempo junta o que cada dominio registra, em ordem cronologica, com o motorista de cada fato. */
@ExtendWith(MockitoExtension.class)
class RastreabilidadeEntregaServiceTest {

    @Mock
    private EntregaService entregaService;
    @Mock
    private BdoService bdoService;
    @Mock
    private ViagemService viagemService;
    @Mock
    private OcorrenciaService ocorrenciaService;
    @Mock
    private DispensaEntregaService dispensaEntregaService;
    @Mock
    private ComprovanteEntregaService comprovanteEntregaService;
    @Mock
    private MotivoOcorrenciaService motivoOcorrenciaService;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;
    @Mock
    private CancelamentoBdoService cancelamentoBdoService;
    @Mock
    private BagagemCaronaService bagagemCaronaService;
    @Mock
    private com.marillia.milebag.business.retirada.service.RetiradaBagagemService retiradaBagagemService;
    @Mock
    private com.marillia.milebag.business.fotoentrega.service.FotoEntregaService fotoEntregaService;

    @Mock
    private com.marillia.milebag.business.segundavia.service.SegundaViaBdoService segundaViaBdoService;
    @InjectMocks
    private RastreabilidadeEntregaService rastreabilidadeEntregaService;

    @Test
    void historicoEmOrdemCronologicaComMotoristaDaOcorrenciaVindoDaViagem() {
        UUID entregaId = UUID.randomUUID();
        UUID ana = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-20T10:00:00Z");

        Entrega entrega = new Entrega();
        entrega.setId(entregaId);
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(EntregaStatus.REABERTA);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));

        Bdo bdo = new Bdo();
        bdo.setNumero("GYN-123");
        bdo.setOrigem(BdoOrigem.EMAIL_AUTOMATICO);
        bdo.setDataCaptura(t0);
        when(bdoService.buscarPorId(entrega.getBdoId())).thenReturn(Optional.of(bdo));

        UUID motivoId = UUID.randomUUID();
        Ocorrencia ocorrencia = new Ocorrencia(entregaId, motivoId, "portao fechado", t0.plusSeconds(7200), false, null);
        ocorrencia.setId(UUID.randomUUID());
        Trajeto viagem = new Trajeto(entregaId, ana, t0.plusSeconds(3600));
        viagem.vincularOcorrencia(ocorrencia.getId(), t0.plusSeconds(7200));
        when(viagemService.listarViagensDaEntrega(entregaId)).thenReturn(List.of(viagem));
        when(ocorrenciaService.listarRegistrosPorEntrega(entregaId)).thenReturn(List.of(ocorrencia));
        when(motivoOcorrenciaService.descricao(motivoId)).thenReturn(Optional.of("Ausência do recebedor"));
        when(usuarioConsultaService.nomeParaExibicao(ana)).thenReturn(Optional.of("Ana"));

        // A dispensa chega antes da ocorrencia na lista, mas aconteceu depois: a ordem final e por data.
        when(dispensaEntregaService.listarPorEntrega(entregaId)).thenReturn(List.of(
                new DispensaEntregaResponse(UUID.randomUUID(), entregaId, "Bruno", "Imprevisto pessoal", null, false, t0.plusSeconds(10800))));
        when(comprovanteEntregaService.buscarPorEntrega(entregaId)).thenReturn(Optional.empty());

        HistoricoEntregaResponse historico = rastreabilidadeEntregaService.historico(entregaId);

        assertThat(historico.entrega().bdoNumero()).isEqualTo("GYN-123");
        assertThat(historico.eventos()).extracting(EventoRastreabilidade::tipo).containsExactly(
                TipoEventoRastreabilidade.BDO_RECEBIDO,
                TipoEventoRastreabilidade.VIAGEM_INICIADA,
                TipoEventoRastreabilidade.OCORRENCIA,
                TipoEventoRastreabilidade.DISPENSA_SEM_BAGAGEM);
        EventoRastreabilidade eventoOcorrencia = historico.eventos().get(2);
        assertThat(eventoOcorrencia.motoristaNome()).isEqualTo("Ana");
        assertThat(eventoOcorrencia.descricao()).isEqualTo("Ausência do recebedor");
        assertThat(eventoOcorrencia.detalhe()).isEqualTo("portao fechado");
        assertThat(historico.eventos().get(3).motoristaNome()).isEqualTo("Bruno");
    }

    @Test
    void historicoDeEntregaInexistenteE404() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rastreabilidadeEntregaService.historico(entregaId)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    /** V47: o BDO que ninguem assumiu entra na lista (sem entrega), marcado se for carona; os mais recentes primeiro. */
    @Test
    void listarTrazOsBdosSemEntregaParaAEmpresaPoderCancelar() {
        Instant t0 = Instant.parse("2026-11-20T10:00:00Z");
        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(EntregaStatus.ATRIBUIDA);
        entrega.setAtualizadoEm(t0);
        when(entregaService.listarParaGestao(null)).thenReturn(List.of(entrega));
        when(bdoService.buscarPorIds(any())).thenReturn(java.util.Map.of());

        Bdo livre = bdoSemEntrega("GYN-NOVO", t0.plusSeconds(60));
        Bdo carona = bdoSemEntrega("GYN-CARONA", t0.minusSeconds(60));
        when(bdoService.listarSemEntrega()).thenReturn(List.of(livre, carona));
        when(bagagemCaronaService.bdosQueSaoCarona(List.of(livre.getId(), carona.getId()))).thenReturn(java.util.Set.of(carona.getId()));

        List<com.marillia.milebag.business.rastreabilidade.model.EntregaGestaoResponse> linhas = rastreabilidadeEntregaService.listar(null);

        assertThat(linhas).extracting(com.marillia.milebag.business.rastreabilidade.model.EntregaGestaoResponse::bdoId)
                .containsExactly(livre.getId(), entrega.getBdoId(), carona.getId());
        assertThat(linhas.get(0).entregaId()).isNull();
        assertThat(linhas.get(0).status()).isEqualTo(EntregaStatus.AGUARDANDO_ATRIBUICAO);
        assertThat(linhas.get(0).bagagemCarona()).isFalse();
        assertThat(linhas.get(2).bagagemCarona()).isTrue();
        assertThat(linhas.get(1).entregaId()).isEqualTo(entrega.getId());
    }

    /** Filtrando por outra situacao, os BDOs sem entrega (que estao aguardando) nao entram. */
    @Test
    void listarPorOutraSituacaoNaoTrazBdosSemEntrega() {
        when(entregaService.listarParaGestao(EntregaStatus.CANCELADA)).thenReturn(List.of());
        when(bdoService.buscarPorIds(any())).thenReturn(java.util.Map.of());

        assertThat(rastreabilidadeEntregaService.listar(EntregaStatus.CANCELADA)).isEmpty();
        org.mockito.Mockito.verify(bdoService, org.mockito.Mockito.never()).listarSemEntrega();
    }

    @Test
    void historicoMostraOCancelamentoComQuemCancelouEOMotivo() {
        UUID entregaId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-20T10:00:00Z");
        Entrega entrega = new Entrega();
        entrega.setId(entregaId);
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(EntregaStatus.CANCELADA);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        when(cancelamentoBdoService.buscarPorEntrega(entregaId)).thenReturn(Optional.of(new CancelamentoBdoResponse(
                UUID.randomUUID(), entrega.getBdoId(), entregaId, "Enviado por engano", "duplicado no e-mail", "Marina", null, false, t0)));

        HistoricoEntregaResponse historico = rastreabilidadeEntregaService.historico(entregaId);

        assertThat(historico.eventos()).singleElement().satisfies(evento -> {
            assertThat(evento.tipo()).isEqualTo(TipoEventoRastreabilidade.CANCELADO);
            assertThat(evento.motoristaNome()).isEqualTo("Marina");
            assertThat(evento.descricao()).isEqualTo("Enviado por engano");
            assertThat(evento.detalhe()).isEqualTo("duplicado no e-mail");
        });
        assertThat(historico.entrega().status()).isEqualTo(EntregaStatus.CANCELADA);
    }

    private static Bdo bdoSemEntrega(String numero, Instant captura) {
        Bdo bdo = new Bdo();
        bdo.setId(UUID.randomUUID());
        bdo.setNumero(numero);
        bdo.setDataCaptura(captura);
        return bdo;
    }

    /** V48: a retirada entra na linha do tempo com o motorista e a observacao das avarias. */
    @Test
    void historicoMostraARetiradaDaBagagem() {
        UUID entregaId = UUID.randomUUID();
        UUID motorista = UUID.randomUUID();
        Entrega entrega = new Entrega();
        entrega.setId(entregaId);
        entrega.setBdoId(UUID.randomUUID());
        entrega.setStatus(EntregaStatus.ATRIBUIDA);
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entrega));
        when(retiradaBagagemService.listarPorEntrega(entregaId)).thenReturn(List.of(new com.marillia.milebag.business.retirada.model.RetiradaBagagem(
                entregaId, motorista, null, null, "arranhao na lateral", Instant.parse("2026-11-21T10:00:00Z"), false, null)));
        when(usuarioConsultaService.nomeParaExibicao(motorista)).thenReturn(Optional.of("Ana"));

        HistoricoEntregaResponse historico = rastreabilidadeEntregaService.historico(entregaId);

        assertThat(historico.eventos()).singleElement().satisfies(evento -> {
            assertThat(evento.tipo()).isEqualTo(TipoEventoRastreabilidade.BAGAGEM_RETIRADA);
            assertThat(evento.motoristaNome()).isEqualTo("Ana");
            assertThat(evento.detalhe()).isEqualTo("arranhao na lateral");
        });
    }

    /** V48: a empresa ve todas as fotos, inclusive a do BDO assinado (do comprovante), na ordem em que foram tiradas. */
    @Test
    void fotosDaEntregaIncluemADoBdoAssinado() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        var retirada = new com.marillia.milebag.business.fotoentrega.model.FotoEntrega(entregaId,
                com.marillia.milebag.business.fotoentrega.model.TipoFotoEntrega.RETIRADA, UUID.randomUUID(), null, "t/r.jpg", Instant.parse("2026-11-21T10:00:00Z"));
        retirada.setId(UUID.randomUUID());
        when(fotoEntregaService.listarPorEntrega(entregaId)).thenReturn(List.of(retirada));
        var comprovante = new com.marillia.milebag.business.comprovante.model.ComprovanteEntrega(
                entregaId, "t/bdo.jpg", "Titular - Ana", Instant.parse("2026-11-21T12:00:00Z"), -16.6, -49.2, false, null);
        comprovante.setId(UUID.randomUUID());
        when(comprovanteEntregaService.buscarPorEntrega(entregaId)).thenReturn(Optional.of(comprovante));
        when(fotoEntregaService.carregarCaminho("t/bdo.jpg")).thenReturn(new com.marillia.milebag.business.fotoentrega.service.FotoEntregaService.Arquivo(new byte[] {9}, "jpg"));

        var fotos = rastreabilidadeEntregaService.fotos(entregaId);

        assertThat(fotos).extracting(com.marillia.milebag.business.rastreabilidade.model.FotoGestaoResponse::tipo).containsExactly("RETIRADA", "BDO_ASSINADO");
        assertThat(rastreabilidadeEntregaService.foto(entregaId, comprovante.getId()).conteudo()).containsExactly(9);
    }
}
