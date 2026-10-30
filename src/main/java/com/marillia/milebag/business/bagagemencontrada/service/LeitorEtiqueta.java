package com.marillia.milebag.business.bagagemencontrada.service;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLeitura;
import com.marillia.milebag.core.ocr.Imagens;
import com.marillia.milebag.core.ocr.LeitorTexto;

/**
 * Le a etiqueta de bagagem na foto (HU34, RF27): primeiro o codigo de barras (ZXing - a etiqueta IATA usa Interleaved
 * 2 of 5 de 10 digitos), na foto como veio e girada 90 graus (a etiqueta costuma ser fotografada de lado); depois o
 * texto impresso (Tesseract), que da os demais campos e, se o codigo nao foi lido, o numero.
 */
@Component
public class LeitorEtiqueta {

    private static final Logger log = LoggerFactory.getLogger(LeitorEtiqueta.class);

    private static final Map<DecodeHintType, Object> DICAS = Map.of(
            DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.ITF, BarcodeFormat.CODE_128),
            DecodeHintType.ALLOWED_LENGTHS, new int[] {10},
            DecodeHintType.TRY_HARDER, Boolean.TRUE);

    private final LeitorTexto leitorTexto;
    private final AeroportoService aeroportoService;

    public LeitorEtiqueta(LeitorTexto leitorTexto, AeroportoService aeroportoService) {
        this.leitorTexto = leitorTexto;
        this.aeroportoService = aeroportoService;
    }

    public LeituraEtiqueta ler(BufferedImage foto) {
        Optional<CodigoLido> codigoDeBarras = lerCodigo(foto);
        // Codigo achado so de lado: o texto tambem esta de lado, e o OCR le a foto endireitada - primeiro no giro que
        // achou o codigo, depois no outro (a foto pode ter sido tirada de qualquer um dos lados).
        List<BufferedImage> paraOTexto = codigoDeBarras.filter(CodigoLido::deLado)
                .map(c -> List.of(Imagens.girar90(foto, false), Imagens.girar90(foto, true)))
                .orElse(List.of(foto));
        String texto = null;
        LeituraEtiqueta doTexto = null;
        for (BufferedImage imagem : paraOTexto) {
            texto = lerTexto(imagem);
            doTexto = EtiquetaTextoParser.extrair(texto, aeroportoService.codigos());
            if (temDadosDoTexto(doTexto)) {
                break;
            }
        }
        if (codigoDeBarras.isEmpty()) {
            return doTexto;
        }
        return new LeituraEtiqueta(
                codigoDeBarras.get().numero(), doTexto.companhia(), doTexto.destino(), doTexto.voo(),
                doTexto.nomePassageiro(), OrigemLeitura.CODIGO_BARRAS, texto);
    }

    static Optional<String> lerCodigoDeBarras(BufferedImage foto) {
        return lerCodigo(foto).map(CodigoLido::numero);
    }

    private String lerTexto(BufferedImage imagem) {
        try {
            return leitorTexto.ler(imagem);
        } catch (RuntimeException e) {
            // Sem texto ainda vale o codigo de barras; a conferencia completa o resto.
            log.warn("OCR da etiqueta falhou - seguindo so com o codigo de barras.", e);
            return null;
        }
    }

    private static boolean temDadosDoTexto(LeituraEtiqueta lida) {
        return lida.companhia() != null || lida.destino() != null || lida.voo() != null || lida.nomePassageiro() != null;
    }

    private static Optional<CodigoLido> lerCodigo(BufferedImage foto) {
        LuminanceSource fonte = new BufferedImageLuminanceSource(foto);
        List<LuminanceSource> tentativas = List.of(fonte, fonte.rotateCounterClockwise());
        for (int i = 0; i < tentativas.size(); i++) {
            try {
                Result resultado = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(tentativas.get(i))), DICAS);
                String lido = resultado.getText();
                if (lido != null && lido.matches("[0-9]{10}")) {
                    // Com TRY_HARDER o proprio ZXing gira a imagem e conta o giro em ORIENTATION (90 ou 270 = de lado).
                    Object giro = resultado.getResultMetadata() == null ? null : resultado.getResultMetadata().get(ResultMetadataType.ORIENTATION);
                    boolean giradoPeloZxing = giro instanceof Integer graus && graus % 180 != 0;
                    return Optional.of(new CodigoLido(lido, (i > 0) != giradoPeloZxing));
                }
            } catch (NotFoundException e) {
                // Nao achou nesta orientacao: tenta a proxima.
            }
        }
        return Optional.empty();
    }

    /** Numero do codigo de barras e se ele so foi achado com a foto girada. */
    private record CodigoLido(String numero, boolean deLado) {
    }
}
