package com.marillia.milebag.business.rota.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.rota.model.CalcularRotaRequest;
import com.marillia.milebag.business.rota.model.CalcularRotaResponse;
import com.marillia.milebag.business.rota.model.PropostaRota;
import com.marillia.milebag.business.rota.model.SelecionarRotaRequest;
import com.marillia.milebag.business.rota.model.SelecionarRotaResponse;
import com.marillia.milebag.business.rota.repository.PropostaRotaRepository;
import com.marillia.milebag.business.viagem.service.SelecaoRotaProvider;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.GeocodingClient;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.RoutingClient;
import com.marillia.milebag.core.geo.TipoSelecao;

/**
 * Cobre o refactor de acoplamento em RotaService: consulta Entrega/Bdo via
 * EntregaService/BdoService (nao mais Repository direto) e implementa
 * {@link SelecaoRotaProvider} para ViagemService ler a rota selecionada sem
 * depender do pacote business.rota (Dependency Inversion).
 */
@ExtendWith(MockitoExtension.class)
class RotaServiceTest {

    @Mock
    private EntregaService entregaService;
    @Mock
    private BdoService bdoService;
    @Mock
    private PropostaRotaRepository propostaRotaRepository;
    @Mock
    private GeocodingClient geocodingClient;
    @Mock
    private RoutingClient routingClient;
    @Mock
    private ViagemService viagemService;

    private RotaService rotaService;

    @BeforeEach
    void setUp() {
        // ObjectMapper real (nao mock): calcular/selecionar dependem de
        // (de)serializacao JSON de verdade para persistir/ler PropostaRota.
        rotaService = new RotaService(
                entregaService, bdoService, propostaRotaRepository, geocodingClient, routingClient, viagemService, new ObjectMapper());
    }

    private Entrega entregaComBdo(UUID entregaId, UUID bdoId) {
        Entrega entrega = new Entrega();
        entrega.setId(entregaId);
        entrega.setBdoId(bdoId);
        return entrega;
    }

    // --- SelecaoRotaProvider.buscarSelecionada ---

    @Test
    void buscarSelecionadaRetornaVazioQuandoNaoHaProposta() {
        UUID entregaId = UUID.randomUUID();
        when(propostaRotaRepository.findById(entregaId)).thenReturn(Optional.empty());

        assertThat(rotaService.buscarSelecionada(entregaId)).isEmpty();
    }

    @Test
    void buscarSelecionadaRetornaVazioQuandoAindaNaoSelecionada() {
        UUID entregaId = UUID.randomUUID();
        PropostaRota proposta = new PropostaRota();
        proposta.setTipoSelecao(null);
        when(propostaRotaRepository.findById(entregaId)).thenReturn(Optional.of(proposta));

        assertThat(rotaService.buscarSelecionada(entregaId)).isEmpty();
    }

    @Test
    void buscarSelecionadaDeserializaRotaEscolhida() throws Exception {
        UUID entregaId = UUID.randomUUID();
        RotaAlternativa alternativa = new RotaAlternativa("principal", 1000.0, 60, false);
        PropostaRota proposta = new PropostaRota();
        proposta.setTipoSelecao(TipoSelecao.SUGERIDA);
        proposta.setRotaEscolhida(new ObjectMapper().writeValueAsString(alternativa));
        when(propostaRotaRepository.findById(entregaId)).thenReturn(Optional.of(proposta));

        Optional<SelecaoRotaProvider.RotaSelecionada> resultado = rotaService.buscarSelecionada(entregaId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().tipoSelecao()).isEqualTo(TipoSelecao.SUGERIDA);
        assertThat(resultado.get().rotaEscolhida()).isEqualTo(alternativa);
    }

    // --- calcular ---

    @Test
    void calcularUsaEntregaServiceEBdoServiceEPersisteProposta() {
        UUID entregaId = UUID.randomUUID();
        UUID bdoId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComBdo(entregaId, bdoId)));
        Bdo bdo = new Bdo();
        bdo.setEnderecoEntrega("Rua Teste, 123");
        when(bdoService.buscarPorId(bdoId)).thenReturn(Optional.of(bdo));
        when(propostaRotaRepository.findById(entregaId)).thenReturn(Optional.empty());

        Coordenada origem = new Coordenada(-23.5, -46.6);
        Coordenada destino = new Coordenada(-23.6, -46.7);
        List<RotaAlternativa> alternativas = List.of(new RotaAlternativa("principal", 5000.0, 600, false));
        when(routingClient.calcularAlternativas(origem, destino)).thenReturn(alternativas);

        CalcularRotaResponse resposta = rotaService.calcular(entregaId, new CalcularRotaRequest(origem, destino));

        assertThat(resposta.origem()).isEqualTo(origem);
        assertThat(resposta.destino()).isEqualTo(destino);
        assertThat(resposta.alternativas()).isEqualTo(alternativas);
        verify(propostaRotaRepository).save(any(PropostaRota.class));
        verify(geocodingClient, never()).geocodificar(anyString());
    }

    @Test
    void calcularLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotaService.calcular(entregaId, new CalcularRotaRequest(new Coordenada(0, 0), null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(propostaRotaRepository, never()).save(any());
    }

    // --- selecionar ---

    @Test
    void selecionarRotaPropriaPersisteTipoSelecaoQuandoViagemNaoEmExecucao() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComBdo(entregaId, UUID.randomUUID())));
        when(propostaRotaRepository.findById(entregaId)).thenReturn(Optional.empty());
        when(viagemService.registrarCalculoRotaSeTrajetoExistir(eq(entregaId), any(), eq(TipoSelecao.PROPRIA)))
                .thenReturn(false);

        SelecionarRotaRequest request = new SelecionarRotaRequest(
                TipoSelecao.PROPRIA, null, List.of(new Coordenada(0, 0), new Coordenada(0, 1)), "desvio necessario");

        SelecionarRotaResponse resposta = rotaService.selecionar(entregaId, request);

        assertThat(resposta.tipoSelecao()).isEqualTo(TipoSelecao.PROPRIA);
        assertThat(resposta.rotaEscolhida().id()).isEqualTo("propria");

        var captor = org.mockito.ArgumentCaptor.forClass(PropostaRota.class);
        verify(propostaRotaRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTipoSelecao()).isEqualTo(TipoSelecao.PROPRIA);
        assertThat(captor.getValue().getJustificativa()).isEqualTo("desvio necessario");
    }

    @Test
    void selecionarNaoAtualizaTipoSelecaoQuandoViagemJaEmExecucao() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComBdo(entregaId, UUID.randomUUID())));
        PropostaRota propostaExistente = new PropostaRota();
        propostaExistente.setTipoSelecao(null);
        when(propostaRotaRepository.findById(entregaId)).thenReturn(Optional.of(propostaExistente));
        when(viagemService.registrarCalculoRotaSeTrajetoExistir(eq(entregaId), any(), eq(TipoSelecao.PROPRIA)))
                .thenReturn(true);

        SelecionarRotaRequest request = new SelecionarRotaRequest(
                TipoSelecao.PROPRIA, null, List.of(new Coordenada(0, 0), new Coordenada(0, 1)), "justificativa");

        rotaService.selecionar(entregaId, request);

        // T013a: trajeto ja em execucao -> vira historico em ViagemService,
        // a proposta pendente NAO recebe tipoSelecao/justificativa.
        assertThat(propostaExistente.getTipoSelecao()).isNull();
        assertThat(propostaExistente.getJustificativa()).isNull();
        // rotaEscolhida e sempre persistida, independente do ramo acima.
        assertThat(propostaExistente.getRotaEscolhida()).isNotNull();
    }

    @Test
    void selecionarSugeridaLocalizaAlternativaPersistida() throws Exception {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(entregaComBdo(entregaId, UUID.randomUUID())));
        RotaAlternativa alternativa = new RotaAlternativa("alt-1", 2000.0, 120, true);
        PropostaRota proposta = new PropostaRota();
        proposta.setAlternativas(new ObjectMapper().writeValueAsString(List.of(alternativa)));
        when(propostaRotaRepository.findById(entregaId)).thenReturn(Optional.of(proposta));
        when(viagemService.registrarCalculoRotaSeTrajetoExistir(eq(entregaId), any(), eq(TipoSelecao.SUGERIDA)))
                .thenReturn(false);

        SelecionarRotaResponse resposta = rotaService.selecionar(
                entregaId, new SelecionarRotaRequest(TipoSelecao.SUGERIDA, "alt-1", null, null));

        assertThat(resposta.rotaEscolhida()).isEqualTo(alternativa);
    }

    @Test
    void selecionarLancaQuandoEntregaNaoExiste() {
        UUID entregaId = UUID.randomUUID();
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotaService.selecionar(entregaId, new SelecionarRotaRequest(TipoSelecao.PROPRIA, null, null, "j")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
