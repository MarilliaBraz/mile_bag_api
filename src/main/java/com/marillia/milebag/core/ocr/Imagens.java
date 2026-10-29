package com.marillia.milebag.core.ocr;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.core.exception.RegraNegocioException;

/** Decodificacao da fotografia enviada (BDO, HU01; etiqueta de bagagem, HU34): formato invalido e erro do usuario (400). */
public final class Imagens {

    private Imagens() {
    }

    public static BufferedImage decodificar(MultipartFile imagem) {
        try {
            BufferedImage imagemDecodificada = ImageIO.read(imagem.getInputStream());
            if (imagemDecodificada == null) {
                throw new RegraNegocioException("Formato de imagem nao suportado.");
            }
            return imagemDecodificada;
        } catch (IOException e) {
            throw new RegraNegocioException("Nao foi possivel ler a fotografia enviada.");
        }
    }

    /** Gira a imagem 90 graus (foto tirada de lado), no sentido horario ou anti-horario. */
    public static BufferedImage girar90(BufferedImage imagem, boolean horario) {
        int largura = imagem.getWidth();
        int altura = imagem.getHeight();
        BufferedImage girada = new BufferedImage(altura, largura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = girada.createGraphics();
        g.translate(horario ? altura : 0, horario ? 0 : largura);
        g.rotate(Math.toRadians(horario ? 90 : -90));
        g.drawImage(imagem, 0, 0, null);
        g.dispose();
        return girada;
    }
}
