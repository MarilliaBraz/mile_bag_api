package com.marillia.milebag.core.ocr;

import java.awt.image.BufferedImage;

import org.springframework.stereotype.Component;

import com.marillia.milebag.core.config.OcrProperties;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Reconhecimento de texto em imagem com o Tesseract (Tess4J) - usado pela captura do BDO por foto (HU01) e pela
 * leitura da etiqueta de bagagem (HU34). Em {@code core}: e mecanismo, sem regra de negocio (CONVENTIONS.md 2.2).
 */
@Component
public class LeitorTexto {

    private final ITesseract tesseract;

    public LeitorTexto(OcrProperties ocrProperties) {
        Tesseract tesseractInstance = new Tesseract();
        tesseractInstance.setDatapath(ocrProperties.tessdataPath());
        // Portugues primeiro: rotulos e enderecos tem acento e cedilha; ingles cobre os rotulos do WorldTracer e das etiquetas.
        tesseractInstance.setLanguage("por+eng");
        this.tesseract = tesseractInstance;
    }

    public String ler(BufferedImage imagem) {
        try {
            return tesseract.doOCR(imagem);
        } catch (TesseractException e) {
            throw new IllegalStateException("Falha ao processar o OCR da fotografia.", e);
        }
    }
}
