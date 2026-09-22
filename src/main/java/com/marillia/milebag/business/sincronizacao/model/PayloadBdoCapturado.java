package com.marillia.milebag.business.sincronizacao.model;

/**
 * Adaptacao de {@code POST /api/v1/bdos} (multipart, HU01) para o transporte
 * JSON do lote offline: a imagem viaja em base64 em vez de multipart/form-data
 * (nao ha como replicar multipart dentro de um item de array JSON).
 */
public record PayloadBdoCapturado(String imagemBase64, String nomeArquivo) {
}
