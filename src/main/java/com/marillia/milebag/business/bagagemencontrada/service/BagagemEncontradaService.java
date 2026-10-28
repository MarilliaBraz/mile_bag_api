package com.marillia.milebag.business.bagagemencontrada.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.model.ConfirmarEtiquetaRequest;
import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLocalizacao;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.ocr.Imagens;
import com.marillia.milebag.core.storage.ArmazenamentoImagemService;

/**
 * Registro de bagagens encontradas pelos funcionarios da companhia aerea (HU34, RF27, RD20). A foto vira um rascunho
 * com a leitura e a localizacao; a conferencia o registra. Filtrado por companhia pela RLS.
 */
@Service
public class BagagemEncontradaService {

    private static final Logger log = LoggerFactory.getLogger(BagagemEncontradaService.class);
    /** Acima disto a tela pede para confirmar o aeroporto sugerido. */
    static final int DISTANCIA_MAXIMA_SUGESTAO_METROS = 50_000;
    /** RD20: 10 digitos do codigo de barras, ou a placa (2 posicoes da companhia + 6 digitos). */
    private static final Pattern NUMERO_ETIQUETA = Pattern.compile("[0-9]{10}|([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z])[0-9]{6}");
    private static final Pattern CODIGO_COMPANHIA = Pattern.compile("[A-Z]{2}|[A-Z][0-9]|[0-9][A-Z]");
    private static final Pattern DESTINO = Pattern.compile("[A-Z]{3}");
    private static final Pattern VOO = Pattern.compile("([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z])[0-9]{1,4}");

    private final BagagemEncontradaRepository repository;
    private final LeitorEtiqueta leitorEtiqueta;
    private final AeroportoService aeroportoService;
    private final ArmazenamentoImagemService armazenamento;
    private final UsuarioConsultaService usuarioConsultaService;

    public BagagemEncontradaService(
            BagagemEncontradaRepository repository, LeitorEtiqueta leitorEtiqueta, AeroportoService aeroportoService,
            ArmazenamentoImagemService armazenamento, UsuarioConsultaService usuarioConsultaService) {
        this.repository = repository;
        this.leitorEtiqueta = leitorEtiqueta;
        this.aeroportoService = aeroportoService;
        this.armazenamento = armazenamento;
        this.usuarioConsultaService = usuarioConsultaService;
    }

    /**
     * Le a foto da etiqueta e guarda o rascunho (RF27). A posicao da foto (EXIF) vale mais que a do aparelho: e de onde a
     * bagagem foi fotografada, nao de onde a foto foi enviada.
     */
    @Transactional
    public BagagemEncontradaResponse lerEtiqueta(MultipartFile foto, Double latitudeAparelho, Double longitudeAparelho, UUID funcionarioId) {
        if (foto == null || foto.isEmpty()) {
            throw new RegraNegocioException("Envie a foto da etiqueta.");
        }
        BufferedImage imagem = Imagens.decodificar(foto);
        LeituraEtiqueta leitura = leitorEtiqueta.ler(imagem);

        Optional<Coordenada> daFoto = LocalizacaoFoto.ler(bytes(foto));
        Coordenada posicao = daFoto.orElse(posicaoValida(latitudeAparelho, longitudeAparelho));
        OrigemLocalizacao origem = daFoto.isPresent() ? OrigemLocalizacao.FOTO : posicao != null ? OrigemLocalizacao.APARELHO : OrigemLocalizacao.NENHUMA;
        Optional<AeroportoService.AeroportoProximo> proximo =
                posicao == null ? Optional.empty() : aeroportoService.maisProximo(posicao.latitude(), posicao.longitude());

        String caminho;
        try {
            caminho = armazenamento.salvar(TenantContext.get(), foto);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao guardar a foto da etiqueta.", e);
        }
        BagagemEncontrada rascunho = repository.save(BagagemEncontrada.rascunhoComEtiqueta(
                funcionarioId, caminho, leitura,
                posicao == null ? null : posicao.latitude(), posicao == null ? null : posicao.longitude(), origem,
                proximo.map(p -> p.aeroporto().getCodigo()).orElse(null), proximo.map(AeroportoService.AeroportoProximo::distanciaMetros).orElse(null)));
        log.info("Etiqueta lida ({}), localizacao {}: rascunho {}", leitura.origem(), origem, rascunho.getId());
        return resposta(rascunho);
    }

    @Transactional
    public BagagemEncontradaResponse confirmarEtiqueta(UUID id, ConfirmarEtiquetaRequest request, UUID funcionarioId) {
        BagagemEncontrada bagagem = buscarRascunhoDoAutor(id, funcionarioId);
        if (request == null) {
            throw new RegraNegocioException("Informe os dados da etiqueta.");
        }
        String numero = maiusculoSemEspacos(request.numeroEtiqueta());
        if (numero == null || !NUMERO_ETIQUETA.matcher(numero).matches()) {
            throw new RegraNegocioException("numeroEtiqueta deve ter 10 digitos ou 2 posicoes da companhia e 6 digitos (ex.: LA123456).");
        }
        String companhia = opcional(maiusculoSemEspacos(request.companhia()), CODIGO_COMPANHIA, "companhia deve ter duas posicoes (ex.: LA, G3).");
        String destino = opcional(maiusculoSemEspacos(request.destino()), DESTINO, "destino deve ser um codigo de aeroporto de 3 letras.");
        String voo = opcional(maiusculoSemEspacos(request.voo()), VOO, "voo deve ser a companhia e o numero (ex.: LA3456).");
        String nome = request.nomePassageiro() == null || request.nomePassageiro().isBlank() ? null : request.nomePassageiro().trim().toUpperCase(Locale.ROOT);
        if (nome != null && nome.length() > 120) {
            throw new RegraNegocioException("nomePassageiro deve ter no maximo 120 caracteres.");
        }
        Aeroporto aeroporto = aeroportoService.buscar(request.aeroportoCodigo())
                .orElseThrow(() -> new RegraNegocioException("Escolha um aeroporto da lista."));
        // A distancia so tem sentido para a posicao do registro; trocar o aeroporto recalcula.
        Integer distancia = bagagem.temLocalizacao()
                ? (int) Math.round(AeroportoService.distanciaMetros(bagagem.getLatitude(), bagagem.getLongitude(), aeroporto.getLatitude(), aeroporto.getLongitude()))
                : null;
        bagagem.registrarEtiqueta(numero, companhia, destino, voo, nome, aeroporto.getCodigo(), distancia, Instant.now());
        return resposta(repository.save(bagagem));
    }

    /** So o rascunho do proprio funcionario: um registro conferido nunca e apagado (RD20). */
    @Transactional
    public void descartar(UUID id, UUID funcionarioId) {
        BagagemEncontrada bagagem = buscarRascunhoDoAutor(id, funcionarioId);
        repository.delete(bagagem);
        try {
            armazenamento.remover(bagagem.getFoto());
        } catch (IOException e) {
            log.warn("Rascunho {} descartado, mas a foto {} nao foi removida.", id, bagagem.getFoto(), e);
        }
    }

    /** {@code RASCUNHO}: os do proprio funcionario; {@code REGISTRADA}: os 20 mais recentes da companhia. */
    @Transactional(readOnly = true)
    public List<BagagemEncontradaResponse> listar(SituacaoBagagemEncontrada situacao, UUID funcionarioId) {
        List<BagagemEncontrada> bagagens = situacao == SituacaoBagagemEncontrada.RASCUNHO
                ? repository.findByCriadoPorAndSituacaoOrderByCriadoEmDesc(funcionarioId, SituacaoBagagemEncontrada.RASCUNHO)
                : repository.findTop20BySituacaoOrderByRegistradaEmDesc(SituacaoBagagemEncontrada.REGISTRADA);
        return bagagens.stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public BagagemEncontradaResponse buscar(UUID id) {
        return resposta(buscarOuFalhar(id));
    }

    @Transactional(readOnly = true)
    public Foto foto(UUID id) {
        BagagemEncontrada bagagem = buscarOuFalhar(id);
        try {
            String caminho = bagagem.getFoto();
            return new Foto(armazenamento.carregar(caminho), caminho.substring(caminho.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar a foto da bagagem.", e);
        }
    }

    public record Foto(byte[] conteudo, String extensao) {
    }

    private BagagemEncontrada buscarOuFalhar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Bagagem nao encontrada."));
    }

    private BagagemEncontrada buscarRascunhoDoAutor(UUID id, UUID funcionarioId) {
        BagagemEncontrada bagagem = buscarOuFalhar(id);
        if (!bagagem.getCriadoPor().equals(funcionarioId)) {
            throw new RecursoNaoEncontradoException("Bagagem nao encontrada.");
        }
        if (!bagagem.rascunho()) {
            throw new ConflitoException("Esta bagagem ja foi registrada.");
        }
        return bagagem;
    }

    private BagagemEncontradaResponse resposta(BagagemEncontrada b) {
        BagagemEncontradaResponse.Etiqueta etiqueta = b.rascunho() ? null
                : new BagagemEncontradaResponse.Etiqueta(b.getNumeroEtiqueta(), b.getCompanhia(), b.getDestino(), b.getVoo(), b.getNomePassageiro());
        BagagemEncontradaResponse.AeroportoRegistro aeroporto = aeroportoService.buscar(b.getAeroportoCodigo())
                .map(a -> new BagagemEncontradaResponse.AeroportoRegistro(
                        a.getCodigo(), a.getNome(), a.getCidade(), a.getUf(), b.getDistanciaAeroportoMetros(),
                        b.getDistanciaAeroportoMetros() == null ? null : b.getDistanciaAeroportoMetros() > DISTANCIA_MAXIMA_SUGESTAO_METROS))
                .orElse(null);
        return new BagagemEncontradaResponse(
                b.getId(), b.getTipo(), b.getSituacao(),
                new BagagemEncontradaResponse.Lido(
                        b.getLidoNumeroEtiqueta(), b.getLidoCompanhia(), b.getLidoDestino(), b.getLidoVoo(), b.getLidoNomePassageiro(), b.getOrigemLeitura()),
                etiqueta,
                new BagagemEncontradaResponse.Localizacao(b.getLatitude(), b.getLongitude(), b.getOrigemLocalizacao()),
                aeroporto,
                usuarioConsultaService.nomeParaExibicao(b.getCriadoPor()).orElse(null),
                b.getRegistradaEm(),
                b.getCriadoEm(),
                "/api/v1/companhia/bagagens/" + b.getId() + "/foto");
    }

    private static Coordenada posicaoValida(Double latitude, Double longitude) {
        if (latitude == null || longitude == null || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            return null;
        }
        return new Coordenada(latitude, longitude);
    }

    private static byte[] bytes(MultipartFile foto) {
        try {
            return foto.getBytes();
        } catch (IOException e) {
            throw new RegraNegocioException("Nao foi possivel ler a fotografia enviada.");
        }
    }

    private static String maiusculoSemEspacos(String valor) {
        return valor == null || valor.isBlank() ? null : valor.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String opcional(String valor, Pattern formato, String mensagem) {
        if (valor != null && !formato.matcher(valor).matches()) {
            throw new RegraNegocioException(mensagem);
        }
        return valor;
    }
}
