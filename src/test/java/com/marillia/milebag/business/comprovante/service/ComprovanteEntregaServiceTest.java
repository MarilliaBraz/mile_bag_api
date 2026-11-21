package com.marillia.milebag.business.comprovante.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.repository.ComprovanteEntregaRepository;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService.DadosDaEntrega;
import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.fotoentrega.model.TipoFotoEntrega;
import com.marillia.milebag.business.fotoentrega.service.FotoEntregaService;
import com.marillia.milebag.business.mensagem.model.TipoEventoNotificacao;
import com.marillia.milebag.business.mensagem.service.MensagemService;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ComprovanteIncompletoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

@ExtendWith(MockitoExtension.class)
class ComprovanteEntregaServiceTest {

    @Mock
    private ComprovanteEntregaRepository comprovanteEntregaRepository;
    @Mock
    private EntregaService entregaService;
    @Mock
    private ViagemService viagemService;
    @Mock
    private MensagemService mensagemService;
    @Mock
    private FotoEntregaService fotoEntregaService;
    @Mock
    private MotivoOcorrenciaService motivoOcorrenciaService;

    private final UUID entregaId = UUID.randomUUID();
    private final UUID tipoId = UUID.randomUUID();
    private final MockMultipartFile fotoBdo = foto("bdo.jpg");
    private final MockMultipartFile fotoPorta = foto("porta.jpg");

    /** Data de corte do formato antigo (R): no futuro aceita, no passado recusa. */
    private ComprovanteEntregaService servico(Instant formatoAntigoAceitoAte) {
        return new ComprovanteEntregaService(
                comprovanteEntregaRepository, entregaService, viagemService, mensagemService,
                fotoEntregaService, motivoOcorrenciaService, formatoAntigoAceitoAte);
    }

    private ComprovanteEntregaService servicoAtual() {
        return servico(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private void entregaLivreParaRegistrar() throws Exception {
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(comprovanteEntregaRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
        when(fotoEntregaService.gravarArquivo(any())).thenReturn("tenant/bdo.jpg");
        when(comprovanteEntregaRepository.save(any(ComprovanteEntrega.class))).thenAnswer(inv -> {
            ComprovanteEntrega comprovante = inv.getArgument(0);
            comprovante.setId(UUID.randomUUID());
            return comprovante;
        });
    }

    private void entregaLivreParaValidar() {
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(comprovanteEntregaRepository.findByEntregaId(entregaId)).thenReturn(Optional.empty());
    }

    /** M5: o reenvio da fila offline devolve o que ja foi gravado, antes de validar de novo. */
    @Test
    void reenvioDoMesmoIdLocalDevolveOComprovanteSemValidarDeNovo() {
        ComprovanteEntrega gravado = new ComprovanteEntrega(entregaId, "t/bdo.jpg", "Titular - Ana", Instant.now(), -16.6, -49.2, true, "id-local");
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.of(new Entrega()));
        when(comprovanteEntregaRepository.findByEntregaId(entregaId)).thenReturn(Optional.of(gravado));

        var resultado = registrar(servicoAtual(), new DadosDaEntrega(null, null, null, List.of(), null));

        assertThat(resultado.novo()).isFalse();
        assertThat(resultado.comprovante()).isSameAs(gravado);
        verify(motivoOcorrenciaService, never()).buscarDaFinalidadeOuFalhar(any(), any());
    }

    private void tipoRecebedorAtivo() {
        MotivoOcorrencia porteiro = new MotivoOcorrencia("Porteiro ou portaria", false, FinalidadeMotivo.RECEBEDOR);
        ReflectionTestUtils.setField(porteiro, "id", tipoId);
        when(motivoOcorrenciaService.buscarDaFinalidadeOuFalhar(tipoId, FinalidadeMotivo.RECEBEDOR)).thenReturn(porteiro);
    }

    private ComprovanteEntregaService.ComprovanteEntregaResultado registrar(ComprovanteEntregaService servico, DadosDaEntrega dados) {
        return servico.registrar(entregaId, fotoBdo, null, Instant.now().toString(), -16.6, -49.2, "id-local", null, dados);
    }

    @Test
    void entregaCompletaGravaQuemAssinouAFotoNaPortaEAsAvariasEEncerra() throws Exception {
        entregaLivreParaRegistrar();
        tipoRecebedorAtivo();

        var resultado = registrar(servicoAtual(), new DadosDaEntrega(
                fotoPorta, tipoId, "  Joao da Silva ", List.of(foto("a1.jpg"), foto("a2.jpg")), "alca quebrada"));

        ComprovanteEntrega comprovante = resultado.comprovante();
        assertThat(comprovante.getIdentificacaoRecebedor()).isEqualTo("Porteiro ou portaria - Joao da Silva");
        assertThat(comprovante.getTipoRecebedorId()).isEqualTo(tipoId);
        assertThat(comprovante.getNomeRecebedor()).isEqualTo("Joao da Silva");
        assertThat(comprovante.getObservacaoAvaria()).isEqualTo("alca quebrada");
        verify(fotoEntregaService).salvarDoComprovante(eq(entregaId), eq(comprovante.getId()), eq(TipoFotoEntrega.PORTA), eq(fotoPorta), any());
        verify(fotoEntregaService, times(2)).salvarDoComprovante(eq(entregaId), eq(comprovante.getId()), eq(TipoFotoEntrega.AVARIA_ENTREGA), any(), any());
        verify(viagemService).encerrarTrajeto(any(), any());
        verify(entregaService).marcarEncerradaComSucesso(entregaId);
        verify(mensagemService).notificarAutomatica(entregaId, TipoEventoNotificacao.COMPROVANTE_REGISTRADO);
    }

    @Test
    void fotoNaPortaEObrigatoria() throws Exception {
        entregaLivreParaValidar();
        assertThatThrownBy(() -> registrar(servicoAtual(), new DadosDaEntrega(null, tipoId, "Joao", List.of(), null)))
                .isInstanceOf(ComprovanteIncompletoException.class).hasMessageContaining("porta");
        verify(comprovanteEntregaRepository, never()).save(any());
    }

    @Test
    void quemAssinouENomeSaoObrigatorios() throws Exception {
        entregaLivreParaValidar();
        assertThatThrownBy(() -> registrar(servicoAtual(), new DadosDaEntrega(fotoPorta, null, "Joao", List.of(), null)))
                .isInstanceOf(ComprovanteIncompletoException.class);
        assertThatThrownBy(() -> registrar(servicoAtual(), new DadosDaEntrega(fotoPorta, tipoId, "  ", List.of(), null)))
                .isInstanceOf(ComprovanteIncompletoException.class);
        verify(comprovanteEntregaRepository, never()).save(any());
    }

    @Test
    void maisDeCincoFotosDeAvariaSaoRecusadas() throws Exception {
        entregaLivreParaValidar();
        tipoRecebedorAtivo();
        List<MultipartFile> seis = List.of(foto("1"), foto("2"), foto("3"), foto("4"), foto("5"), foto("6"));

        assertThatThrownBy(() -> registrar(servicoAtual(), new DadosDaEntrega(fotoPorta, tipoId, "Joao", seis, null)))
                .isInstanceOf(RegraNegocioException.class);
        verify(comprovanteEntregaRepository, never()).save(any());
    }

    /** R: o comprovante sem os dados novos (fila offline de antes da atualizacao) ainda vale ate a data de corte. */
    @Test
    void formatoAntigoAceitoAteADataDeCorte() throws Exception {
        entregaLivreParaRegistrar();

        var resultado = servico(Instant.now().plus(7, ChronoUnit.DAYS)).registrar(
                entregaId, fotoBdo, "Fulano de Tal", Instant.now().toString(), -16.6, -49.2, "id-local", null, null);

        assertThat(resultado.comprovante().getIdentificacaoRecebedor()).isEqualTo("Fulano de Tal");
        verify(fotoEntregaService, never()).salvarDoComprovante(any(), any(), any(), any(), any());
        verify(entregaService).marcarEncerradaComSucesso(entregaId);
    }

    @Test
    void formatoAntigoDepoisDaDataDeCortePedeParaAtualizarOApp() throws Exception {
        entregaLivreParaValidar();
        assertThatThrownBy(() -> servicoAtual().registrar(
                entregaId, fotoBdo, "Fulano", Instant.now().toString(), -16.6, -49.2, "id-local", null, DadosDaEntrega.FORMATO_ANTIGO))
                .isInstanceOf(ComprovanteIncompletoException.class).hasMessageContaining("Atualize o aplicativo");
        verify(comprovanteEntregaRepository, never()).save(any());
    }

    @Test
    void registrarLancaQuandoEntregaNaoExiste() {
        when(entregaService.buscarPorId(entregaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar(servicoAtual(), new DadosDaEntrega(fotoPorta, tipoId, "Joao", List.of(), null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(entregaService, never()).marcarEncerradaComSucesso(any());
        verify(comprovanteEntregaRepository, never()).save(any());
    }

    @Test
    void listarPorPeriodoDelegaParaRepository() {
        Instant inicio = Instant.now().minusSeconds(3600);
        Instant fim = Instant.now();
        when(comprovanteEntregaRepository.findByDataHoraBetween(inicio, fim)).thenReturn(List.of());

        assertThat(servicoAtual().listarPorPeriodo(inicio, fim)).isEmpty();
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(comprovanteEntregaRepository).findByDataHoraBetween(captor.capture(), any());
        assertThat(captor.getValue()).isEqualTo(inicio);
    }

    private static MockMultipartFile foto(String nome) {
        return new MockMultipartFile("foto", nome, "image/jpeg", new byte[] {1, 2, 3});
    }
}
