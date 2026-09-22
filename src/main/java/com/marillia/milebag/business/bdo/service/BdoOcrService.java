package com.marillia.milebag.business.bdo.service;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.core.config.OcrProperties;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.util.CampoRotuladoExtractor;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Extracao automatica dos campos do BDO a partir da fotografia (FR-002),
 * via Tesseract OCR 5 / Tess4J (research.md da feature 001, secao 4).
 *
 * <p>A extracao por rotulo de linha abaixo e uma heuristica de melhor
 * esforco: nenhum layout exato do telex WorldTracer foi fornecido nas
 * evidencias do projeto, apenas os campos de negocio esperados
 * (spec.md, "Key Entities"). Um campo nao reconhecido fica vazio e e
 * preenchido manualmente na tela de conferencia (FR-004) - nunca bloqueia a
 * captura (SC-001 tolera ate 10% de erro, absorvido pela conferencia
 * obrigatoria, RN01).</p>
 */
@Service
public class BdoOcrService {

    private final ITesseract tesseract;

    public BdoOcrService(OcrProperties ocrProperties) {
        Tesseract tesseractInstance = new Tesseract();
        tesseractInstance.setDatapath(ocrProperties.tessdataPath());
        tesseractInstance.setLanguage("eng");
        this.tesseract = tesseractInstance;
    }

    public CamposExtraidosBdo extrair(MultipartFile imagem) {
        BufferedImage imagemDecodificada = decodificar(imagem);
        String textoBruto = reconhecerTexto(imagemDecodificada);

        return new CamposExtraidosBdo(
                CampoRotuladoExtractor.extrair(textoBruto, "BDO", "NUMERO", "NUMBER", "NBR"),
                CampoRotuladoExtractor.extrair(textoBruto, "PAX", "PASSAGEIRO", "NAME"),
                CampoRotuladoExtractor.extrair(textoBruto, "ADDR", "ENDERECO", "ADDRESS", "DELIVERY ADDRESS"),
                CampoRotuladoExtractor.extrair(textoBruto, "FLT", "VOO", "AIRLINE", "CIA", "CARRIER"),
                CampoRotuladoExtractor.extrair(textoBruto, "TEL", "TELEFONE", "PHONE"),
                CampoRotuladoExtractor.extrair(textoBruto, "EMAIL", "E-MAIL"),
                CampoRotuladoExtractor.extrair(textoBruto, "TAG", "ETIQUETA", "BAGGAGE", "BAG TAG"));
    }

    private BufferedImage decodificar(MultipartFile imagem) {
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

    private String reconhecerTexto(BufferedImage imagem) {
        try {
            return tesseract.doOCR(imagem);
        } catch (TesseractException e) {
            throw new IllegalStateException("Falha ao processar o OCR da fotografia do BDO.", e);
        }
    }
}
