package com.marillia.milebag.business.sincronizacao.model;

/** Uma foto do lote offline (V48): conteudo em base64 e o nome do arquivo no aparelho. */
public record FotoOffline(String fotoBase64, String nomeArquivo) {
}
