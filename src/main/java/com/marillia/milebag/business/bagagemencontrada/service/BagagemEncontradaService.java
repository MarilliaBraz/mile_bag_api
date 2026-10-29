package com.marillia.milebag.business.bagagemencontrada.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.model.ConfirmarEtiquetaRequest;
import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.DescreverBagagemRequest;
import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLocalizacao;
import com.marillia.milebag.business.bagagemencontrada.model.SinalParticular;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TamanhoBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.TipoVolume;
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
 * Registro de bagagens encontradas pelos funcionarios da companhia aerea (RD20): com etiqueta (HU34, RF27) ou sem
 * etiqueta (HU35, RF28). A foto vira um rascunho com a leitura (ou a cor sugerida) e a localizacao; a conferencia - ou
 * a descricao, com o codigo SID (RD21) - o registra. Filtrado por companhia pela RLS.
 */
@Service
public class BagagemEncontradaService {

    private static final Logger log = LoggerFactory.getLogger(BagagemEncontradaService.class);
    /** Acima disto a tela pede para confirmar o aeroporto sugerido. */
    static final int DISTANCIA_MAXIMA_SUGESTAO_METROS = 50_000;
    /** RD20: 10 digitos do codigo de barras, ou a placa (2 posicoes da companhia + 6 digitos). */
    static final Pattern NUMERO_ETIQUETA = Pattern.compile("[0-9]{10}|([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z])[0-9]{6}");
    private static final Pattern CODIGO_COMPANHIA = Pattern.compile("[A-Z]{2}|[A-Z][0-9]|[0-9][A-Z]");
    private static final Pattern DESTINO = Pattern.compile("[A-Z]{3}");
    private static final Pattern VOO = Pattern.compile("([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z])[0-9]{1,4}");
    /** RD21: o ano do codigo SID e o de Brasilia; as datas da consulta e da ficha (HU36) tambem. */
    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final int LADO_QR = 360;

    private final BagagemEncontradaRepository repository;
    private final LeitorEtiqueta leitorEtiqueta;
    private final AeroportoService aeroportoService;
    private final ArmazenamentoImagemService armazenamento;
    private final UsuarioConsultaService usuarioConsultaService;
    private final EncaminhamentosDaBagagem encaminhamentos;

    public BagagemEncontradaService(
            BagagemEncontradaRepository repository, LeitorEtiqueta leitorEtiqueta, AeroportoService aeroportoService,
            ArmazenamentoImagemService armazenamento, UsuarioConsultaService usuarioConsultaService, EncaminhamentosDaBagagem encaminhamentos) {
        this.repository = repository;
        this.leitorEtiqueta = leitorEtiqueta;
        this.aeroportoService = aeroportoService;
        this.armazenamento = armazenamento;
        this.usuarioConsultaService = usuarioConsultaService;
        this.encaminhamentos = encaminhamentos;
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
        LeituraEtiqueta leitura = leitorEtiqueta.ler(Imagens.decodificar(foto));
        Local local = localizar(foto, latitudeAparelho, longitudeAparelho);
        BagagemEncontrada rascunho = repository.save(BagagemEncontrada.rascunhoComEtiqueta(
                funcionarioId, guardarFoto(foto), leitura, local.latitude(), local.longitude(), local.origem(), local.aeroporto(), local.distancia()));
        log.info("Etiqueta lida ({}), localizacao {}: rascunho {}", leitura.origem(), local.origem(), rascunho.getId());
        return resposta(rascunho);
    }

    /** Foto de bagagem sem etiqueta (RF28): o rascunho com a cor sugerida pela foto e o local, como na etiqueta. */
    @Transactional
    public BagagemEncontradaResponse lerSemEtiqueta(MultipartFile foto, Double latitudeAparelho, Double longitudeAparelho, UUID funcionarioId) {
        if (foto == null || foto.isEmpty()) {
            throw new RegraNegocioException("Envie a foto da bagagem.");
        }
        CorBagagem cor = CorPredominante.sugerir(Imagens.decodificar(foto)).orElse(null);
        Local local = localizar(foto, latitudeAparelho, longitudeAparelho);
        BagagemEncontrada rascunho = repository.save(BagagemEncontrada.rascunhoSemEtiqueta(
                funcionarioId, guardarFoto(foto), cor, local.latitude(), local.longitude(), local.origem(), local.aeroporto(), local.distancia()));
        log.info("Bagagem sem etiqueta fotografada (cor sugerida {}), localizacao {}: rascunho {}", cor, local.origem(), rascunho.getId());
        return resposta(rascunho);
    }

    /** A descricao da bagagem sem etiqueta (RF28) a registra com o codigo SID do aeroporto escolhido (RD21). */
    @Transactional
    public BagagemEncontradaResponse registrarSemEtiqueta(UUID id, DescreverBagagemRequest request, UUID funcionarioId) {
        BagagemEncontrada bagagem = buscarRascunhoDoAutor(id, funcionarioId);
        if (!bagagem.semEtiqueta()) {
            throw new ConflitoException("Bagagem com etiqueta: confira os dados da etiqueta.");
        }
        if (request == null) {
            throw new RegraNegocioException("Descreva a bagagem.");
        }
        TipoVolume tipoVolume = opcao(TipoVolume.class, request.tipoVolume(), "tipoVolume");
        TamanhoBagagem tamanho = opcao(TamanhoBagagem.class, request.tamanho(), "tamanho");
        CorBagagem cor = opcao(CorBagagem.class, request.cor(), "cor");
        Set<SinalParticular> sinais = EnumSet.noneOf(SinalParticular.class);
        for (String sinal : request.sinais() == null ? List.<String>of() : request.sinais()) {
            sinais.add(opcao(SinalParticular.class, sinal, "sinais"));
        }
        String marca = textoLivre(request.marca(), 60, "marca");
        String observacoes = textoLivre(request.observacoes(), 500, "observacoes");
        Aeroporto aeroporto = aeroportoService.buscar(request.aeroportoCodigo())
                .orElseThrow(() -> new RegraNegocioException("Escolha um aeroporto da lista."));
        Instant agora = Instant.now();
        String codigoSid = String.format(
                Locale.ROOT, "SID-%s-%d-%06d", aeroporto.getCodigo(), agora.atZone(BRASILIA).getYear(), repository.proximoNumeroSid());
        bagagem.registrarDescricao(
                tipoVolume, tamanho, cor, sinais, marca, observacoes, aeroporto.getCodigo(), distanciaAte(bagagem, aeroporto), codigoSid, agora);
        log.info("Bagagem sem etiqueta {} registrada: {}", id, codigoSid);
        return resposta(repository.save(bagagem));
    }

    /** QR code do codigo SID (RD21), para a etiqueta impressa. So existe na bagagem sem etiqueta registrada. */
    @Transactional(readOnly = true)
    public byte[] qrSid(UUID id) {
        BagagemEncontrada bagagem = buscarOuFalhar(id);
        if (bagagem.getCodigoSid() == null) {
            throw new RecursoNaoEncontradoException("Esta bagagem nao tem codigo SID.");
        }
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(
                    new QRCodeWriter().encode(bagagem.getCodigoSid(), BarcodeFormat.QR_CODE, LADO_QR, LADO_QR, Map.of(EncodeHintType.MARGIN, 2)),
                    "png", png);
            return png.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Falha ao gerar o QR code do codigo SID.", e);
        }
    }

    @Transactional
    public BagagemEncontradaResponse confirmarEtiqueta(UUID id, ConfirmarEtiquetaRequest request, UUID funcionarioId) {
        BagagemEncontrada bagagem = buscarRascunhoDoAutor(id, funcionarioId);
        if (bagagem.semEtiqueta()) {
            throw new ConflitoException("Bagagem sem etiqueta: registre pela descricao.");
        }
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
        bagagem.registrarEtiqueta(numero, companhia, destino, voo, nome, aeroporto.getCodigo(), distanciaAte(bagagem, aeroporto), Instant.now());
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

    /** Onde a bagagem foi fotografada e o aeroporto sugerido; a posicao da foto vale mais que a do aparelho. */
    private Local localizar(MultipartFile foto, Double latitudeAparelho, Double longitudeAparelho) {
        Optional<Coordenada> daFoto = LocalizacaoFoto.ler(bytes(foto));
        Coordenada posicao = daFoto.orElse(posicaoValida(latitudeAparelho, longitudeAparelho));
        OrigemLocalizacao origem = daFoto.isPresent() ? OrigemLocalizacao.FOTO : posicao != null ? OrigemLocalizacao.APARELHO : OrigemLocalizacao.NENHUMA;
        Optional<AeroportoService.AeroportoProximo> proximo =
                posicao == null ? Optional.empty() : aeroportoService.maisProximo(posicao.latitude(), posicao.longitude());
        return new Local(
                posicao == null ? null : posicao.latitude(), posicao == null ? null : posicao.longitude(), origem,
                proximo.map(p -> p.aeroporto().getCodigo()).orElse(null), proximo.map(AeroportoService.AeroportoProximo::distanciaMetros).orElse(null));
    }

    private record Local(Double latitude, Double longitude, OrigemLocalizacao origem, String aeroporto, Integer distancia) {
    }

    private String guardarFoto(MultipartFile foto) {
        try {
            return armazenamento.salvar(TenantContext.get(), foto);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao guardar a foto da bagagem.", e);
        }
    }

    /** A distancia so tem sentido para a posicao do registro; trocar o aeroporto recalcula. */
    private static Integer distanciaAte(BagagemEncontrada bagagem, Aeroporto aeroporto) {
        return bagagem.temLocalizacao()
                ? (int) Math.round(AeroportoService.distanciaMetros(bagagem.getLatitude(), bagagem.getLongitude(), aeroporto.getLatitude(), aeroporto.getLongitude()))
                : null;
    }

    BagagemEncontradaResponse resposta(BagagemEncontrada b) {
        BagagemEncontradaResponse.Etiqueta etiqueta = b.rascunho() || b.semEtiqueta() ? null
                : new BagagemEncontradaResponse.Etiqueta(b.getNumeroEtiqueta(), b.getCompanhia(), b.getDestino(), b.getVoo(), b.getNomePassageiro());
        BagagemEncontradaResponse.Descricao descricao = b.rascunho() || !b.semEtiqueta() ? null
                : new BagagemEncontradaResponse.Descricao(
                        b.getTipoVolume(), b.getTamanho(), b.getCor(), List.copyOf(b.getSinais()), b.getMarca(), b.getObservacoes(), b.getCodigoSid());
        BagagemEncontradaResponse.AeroportoRegistro aeroporto = aeroportoService.buscar(b.getAeroportoCodigo())
                .map(a -> new BagagemEncontradaResponse.AeroportoRegistro(
                        a.getCodigo(), a.getNome(), a.getCidade(), a.getUf(), b.getDistanciaAeroportoMetros(),
                        b.getDistanciaAeroportoMetros() == null ? null : b.getDistanciaAeroportoMetros() > DISTANCIA_MAXIMA_SUGESTAO_METROS))
                .orElse(null);
        return new BagagemEncontradaResponse(
                b.getId(), b.getTipo(), b.getSituacao(),
                new BagagemEncontradaResponse.Lido(
                        b.getLidoNumeroEtiqueta(), b.getLidoCompanhia(), b.getLidoDestino(), b.getLidoVoo(), b.getLidoNomePassageiro(), b.getOrigemLeitura(),
                        b.getLidoCor()),
                etiqueta,
                descricao,
                new BagagemEncontradaResponse.Localizacao(b.getLatitude(), b.getLongitude(), b.getOrigemLocalizacao()),
                aeroporto,
                usuarioConsultaService.nomeParaExibicao(b.getCriadoPor()).orElse(null),
                b.getRegistradaEm(),
                b.getCriadoEm(),
                "/api/v1/companhia/bagagens/" + b.getId() + "/foto",
                b.getCodigoSid() == null ? null : "/api/v1/companhia/bagagens/" + b.getId() + "/qr",
                b.rascunho() ? null : encaminhamentos.ativo(b.getId()).orElse(null));
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

    private static <E extends Enum<E>> E opcao(Class<E> opcoes, String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException(campo + " e obrigatorio.");
        }
        try {
            return Enum.valueOf(opcoes, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException(campo + " fora das opcoes: " + valor + ".");
        }
    }

    private static String textoLivre(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > maximo) {
            throw new RegraNegocioException(campo + " deve ter no maximo " + maximo + " caracteres.");
        }
        return limpo;
    }

    private static String opcional(String valor, Pattern formato, String mensagem) {
        if (valor != null && !formato.matcher(valor).matches()) {
            throw new RegraNegocioException(mensagem);
        }
        return valor;
    }
}
