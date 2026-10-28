package com.marillia.milebag.core.ocr;

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
}
