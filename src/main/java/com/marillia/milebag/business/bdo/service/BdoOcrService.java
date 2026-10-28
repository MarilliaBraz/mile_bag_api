package com.marillia.milebag.business.bdo.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.core.ocr.Imagens;
import com.marillia.milebag.core.ocr.LeitorTexto;

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

    private final LeitorTexto leitorTexto;

    public BdoOcrService(LeitorTexto leitorTexto) {
        this.leitorTexto = leitorTexto;
    }

    public CamposExtraidosBdo extrair(MultipartFile imagem) {
        return CamposBdoExtractor.extrair(leitorTexto.ler(Imagens.decodificar(imagem)));
    }
}
