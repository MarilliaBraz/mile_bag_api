package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Caminho dos dados de treino do Tesseract (Tess4J, ver research.md da
 * feature 001, secao 4) - sempre vindo de configuracao externa, nunca
 * hardcoded, ver {@code CONVENTIONS.md} secao 2.3.
 */
@ConfigurationProperties(prefix = "milebag.ocr")
public record OcrProperties(String tessdataPath) {
}
