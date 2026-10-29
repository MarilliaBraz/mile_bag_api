package com.marillia.milebag.business.bagagemencontrada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.oned.ITFWriter;
import com.marillia.milebag.business.bagagemencontrada.model.LeituraEtiqueta;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLeitura;
import com.marillia.milebag.core.ocr.LeitorTexto;

/** Le codigos de barras de verdade, gerados pelo proprio ZXing no formato da etiqueta IATA (Interleaved 2 of 5, 10 digitos). */
class LeitorEtiquetaTest {

    @Test
    void leOCodigoDeBarrasDeFrente() {
        assertThat(LeitorEtiqueta.lerCodigoDeBarras(etiqueta("0957123456"))).contains("0957123456");
    }

    /** A etiqueta costuma ser fotografada de lado. */
    @Test
    void leOCodigoDeBarrasGirado() {
        assertThat(LeitorEtiqueta.lerCodigoDeBarras(girar90(etiqueta("0123456789")))).contains("0123456789");
    }

    @Test
    void semCodigoDeBarrasNaoInventaNumero() {
        assertThat(LeitorEtiqueta.lerCodigoDeBarras(new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB))).isEmpty();
    }

    /** O numero vem do codigo de barras; os demais campos, do texto. */
    @Test
    void juntaOCodigoDeBarrasComOTexto() {
        LeitorTexto leitorTexto = mock(LeitorTexto.class);
        when(leitorTexto.ler(any())).thenReturn("GYN\nSILVA/MARIA\nLA 999999");
        AeroportoService aeroportos = mock(AeroportoService.class);
        when(aeroportos.codigos()).thenReturn(Set.of("GYN"));

        LeituraEtiqueta lida = new LeitorEtiqueta(leitorTexto, aeroportos).ler(etiqueta("0957123456"));

        assertThat(lida.numeroEtiqueta()).isEqualTo("0957123456");
        assertThat(lida.origem()).isEqualTo(OrigemLeitura.CODIGO_BARRAS);
        assertThat(lida.destino()).isEqualTo("GYN");
        assertThat(lida.companhia()).isEqualTo("LA");
        assertThat(lida.nomePassageiro()).isEqualTo("SILVA/MARIA");
    }

    /** Foto de lado: o texto tambem esta de lado, e o OCR recebe a foto endireitada (deitada, como a etiqueta). */
    @Test
    void etiquetaDeLadoLeOTextoNaFotoEndireitada() {
        LeitorTexto leitorTexto = mock(LeitorTexto.class);
        when(leitorTexto.ler(argThat(imagem -> imagem != null && imagem.getWidth() > imagem.getHeight())))
                .thenReturn("GYN\nSOUZA/MARIA\nG3 123456");
        AeroportoService aeroportos = mock(AeroportoService.class);
        when(aeroportos.codigos()).thenReturn(Set.of("GYN"));

        LeituraEtiqueta lida = new LeitorEtiqueta(leitorTexto, aeroportos).ler(girar90(etiqueta("0127654321")));

        assertThat(lida.numeroEtiqueta()).isEqualTo("0127654321");
        assertThat(lida.destino()).isEqualTo("GYN");
        assertThat(lida.nomePassageiro()).isEqualTo("SOUZA/MARIA");
    }

    @Test
    void falhaDoTextoNaoPerdeOCodigoDeBarras() {
        LeitorTexto leitorTexto = mock(LeitorTexto.class);
        when(leitorTexto.ler(any())).thenThrow(new IllegalStateException("tesseract"));
        AeroportoService aeroportos = mock(AeroportoService.class);
        when(aeroportos.codigos()).thenReturn(Set.of());

        assertThat(new LeitorEtiqueta(leitorTexto, aeroportos).ler(etiqueta("0957123456")).numeroEtiqueta()).isEqualTo("0957123456");
    }

    /** Codigo de barras com margem branca, como numa etiqueta impressa. */
    private static BufferedImage etiqueta(String numero) {
        try {
            BufferedImage codigo = MatrixToImageWriter.toBufferedImage(new ITFWriter().encode(numero, BarcodeFormat.ITF, 600, 160));
            BufferedImage folha = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = folha.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 800, 400);
            g.drawImage(codigo, 100, 120, null);
            g.dispose();
            return folha;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static BufferedImage girar90(BufferedImage imagem) {
        BufferedImage girada = new BufferedImage(imagem.getHeight(), imagem.getWidth(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < imagem.getWidth(); x++) {
            for (int y = 0; y < imagem.getHeight(); y++) {
                girada.setRGB(imagem.getHeight() - 1 - y, x, imagem.getRGB(x, y));
            }
        }
        return girada;
    }
}
