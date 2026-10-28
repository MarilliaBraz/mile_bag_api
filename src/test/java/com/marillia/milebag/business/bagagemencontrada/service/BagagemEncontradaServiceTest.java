package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.model.ConfirmarEtiquetaRequest;
import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLeitura;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLocalizacao;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.repository.AeroportoRepository;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;

@ExtendWith(MockitoExtension.class)
class BagagemEncontradaServiceTest {

    private static final Aeroporto GRU = new Aeroporto("GRU", "Guarulhos", "São Paulo", "SP", -23.431274, -46.469954);
    private static final Aeroporto GYN = new Aeroporto("GYN", "Santa Genoveva", "Goiânia", "GO", -16.632000, -49.220699);

    @Mock
    private BagagemEncontradaRepository repository;
    @Mock
    private LeitorEtiqueta leitorEtiqueta;
    @Mock
    private AeroportoRepository aeroportoRepository;
    @Mock
    private ArmazenamentoImagemService armazenamento;
    @Mock
    private UsuarioConsultaService usuarioConsultaService;

    private BagagemEncontradaService service;
    private final UUID funcionario = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(aeroportoRepository.findAllByOrderByCodigoAsc()).thenReturn(List.of(GRU, GYN));
        service = new BagagemEncontradaService(repository, leitorEtiqueta, new AeroportoService(aeroportoRepository), armazenamento, usuarioConsultaService);
        lenient().when(repository.save(any(BagagemEncontrada.class))).thenAnswer(chamada -> chamada.getArgument(0));
        lenient().when(armazenamento.salvar(any(), any())).thenReturn("tenant/etiqueta.png");
        lenient().when(usuarioConsultaService.nomeParaExibicao(any())).thenReturn(Optional.of("Rita"));
    }

    @Test
    void semGpsNaFotoUsaOAparelhoESugereOAeroportoMaisProximo() throws Exception {
        when(leitorEtiqueta.ler(any())).thenReturn(new LeituraEtiqueta("0957123456", "LA", "GYN", null, null, OrigemLeitura.CODIGO_BARRAS, "texto"));

        BagagemEncontradaResponse resposta = service.lerEtiqueta(foto(), -23.4356, -46.4731, funcionario);

        assertThat(resposta.situacao()).isEqualTo(SituacaoBagagemEncontrada.RASCUNHO);
        assertThat(resposta.lido().numeroEtiqueta()).isEqualTo("0957123456");
        assertThat(resposta.localizacao().origem()).isEqualTo(OrigemLocalizacao.APARELHO);
        assertThat(resposta.aeroporto().codigo()).isEqualTo("GRU");
        assertThat(resposta.aeroporto().distante()).isFalse();
        assertThat(resposta.etiqueta()).isNull();
    }

    @Test
    void semLocalizacaoNenhumaFicaSemAeroporto() throws Exception {
        when(leitorEtiqueta.ler(any())).thenReturn(LeituraEtiqueta.vazia(null));

        BagagemEncontradaResponse resposta = service.lerEtiqueta(foto(), null, null, funcionario);

        assertThat(resposta.localizacao().origem()).isEqualTo(OrigemLocalizacao.NENHUMA);
        assertThat(resposta.aeroporto()).isNull();
        assertThat(resposta.lido().origemLeitura()).isEqualTo(OrigemLeitura.NENHUMA);
    }

    @Test
    void longeDeTodoAeroportoASugestaoVemMarcadaComoDistante() throws Exception {
        when(leitorEtiqueta.ler(any())).thenReturn(LeituraEtiqueta.vazia(null));

        // Palmas (TO): o mais proximo da lista de teste fica a centenas de km.
        assertThat(service.lerEtiqueta(foto(), -10.18, -48.33, funcionario).aeroporto().distante()).isTrue();
    }

    @Test
    void conferenciaRegistraEmMaiusculasSemEspacos() {
        BagagemEncontrada rascunho = rascunho(funcionario);
        when(repository.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        BagagemEncontradaResponse resposta = service.confirmarEtiqueta(
                rascunho.getId(), new ConfirmarEtiquetaRequest("la 123456", "la", "gyn", "la 3456", "silva/maria", "GRU"), funcionario);

        assertThat(resposta.situacao()).isEqualTo(SituacaoBagagemEncontrada.REGISTRADA);
        assertThat(resposta.etiqueta().numeroEtiqueta()).isEqualTo("LA123456");
        assertThat(resposta.etiqueta().voo()).isEqualTo("LA3456");
        assertThat(resposta.etiqueta().nomePassageiro()).isEqualTo("SILVA/MARIA");
        assertThat(resposta.aeroporto().codigo()).isEqualTo("GRU");
        assertThat(resposta.registradaEm()).isNotNull();
    }

    @Test
    void numeroForaDoFormatoOuAeroportoInexistenteE400() {
        BagagemEncontrada rascunho = rascunho(funcionario);
        when(repository.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        assertThatThrownBy(() -> service.confirmarEtiqueta(rascunho.getId(), new ConfirmarEtiquetaRequest("12345", null, null, null, null, "GRU"), funcionario))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.confirmarEtiqueta(rascunho.getId(), new ConfirmarEtiquetaRequest("0957123456", null, null, null, null, "XXX"), funcionario))
                .isInstanceOf(RegraNegocioException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rascunhoDeOutroFuncionarioNaoAparece() {
        BagagemEncontrada deOutro = rascunho(UUID.randomUUID());
        when(repository.findById(deOutro.getId())).thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> service.descartar(deOutro.getId(), funcionario)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void registradaNaoEDescartadaNemConferidaDeNovo() {
        BagagemEncontrada registrada = rascunho(funcionario);
        registrada.registrarEtiqueta("0957123456", null, null, null, null, "GRU", null, java.time.Instant.now());
        when(repository.findById(registrada.getId())).thenReturn(Optional.of(registrada));

        assertThatThrownBy(() -> service.descartar(registrada.getId(), funcionario)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> service.confirmarEtiqueta(registrada.getId(), new ConfirmarEtiquetaRequest("0957123456", null, null, null, null, "GRU"), funcionario))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void descartarApagaORascunhoEAFoto() throws Exception {
        BagagemEncontrada rascunho = rascunho(funcionario);
        when(repository.findById(rascunho.getId())).thenReturn(Optional.of(rascunho));

        service.descartar(rascunho.getId(), funcionario);

        verify(repository).delete(rascunho);
        verify(armazenamento).remover("tenant/etiqueta.png");
    }

    private static BagagemEncontrada rascunho(UUID autor) {
        BagagemEncontrada b = BagagemEncontrada.rascunhoComEtiqueta(
                autor, "tenant/etiqueta.png", LeituraEtiqueta.vazia(null), -23.4356, -46.4731, OrigemLocalizacao.APARELHO, "GRU", 800);
        b.setId(UUID.randomUUID());
        return b;
    }

    private static MockMultipartFile foto() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", png);
        return new MockMultipartFile("foto", "etiqueta.png", "image/png", png.toByteArray());
    }
}
