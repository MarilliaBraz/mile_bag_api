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
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLeitura;
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
        Optional<String> codigoDeBarras = lerCodigoDeBarras(foto);
        String texto;
        try {
            texto = leitorTexto.ler(foto);
        } catch (RuntimeException e) {
            // Sem texto ainda vale o codigo de barras; a conferencia completa o resto.
            log.warn("OCR da etiqueta falhou - seguindo so com o codigo de barras.", e);
            texto = null;
        }
        LeituraEtiqueta doTexto = EtiquetaTextoParser.extrair(texto, aeroportoService.codigos());
        if (codigoDeBarras.isEmpty()) {
            return doTexto;
        }
        return new LeituraEtiqueta(
                codigoDeBarras.get(), doTexto.companhia(), doTexto.destino(), doTexto.voo(), doTexto.nomePassageiro(),
                OrigemLeitura.CODIGO_BARRAS, texto);
    }

    static Optional<String> lerCodigoDeBarras(BufferedImage foto) {
        LuminanceSource fonte = new BufferedImageLuminanceSource(foto);
        for (LuminanceSource tentativa : List.of(fonte, fonte.rotateCounterClockwise())) {
            try {
                String lido = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(tentativa)), DICAS).getText();
                if (lido != null && lido.matches("[0-9]{10}")) {
                    return Optional.of(lido);
                }
            } catch (NotFoundException e) {
                // Nao achou nesta orientacao: tenta a proxima.
            }
        }
        return Optional.empty();
    }
}
