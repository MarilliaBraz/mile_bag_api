package com.marillia.milebag.business.sincronizacao.model;

import java.util.List;
import java.util.UUID;

/** Adaptacao de {@code POST /retirada} (multipart, V48) para o lote offline: fotos em base64. */
public record PayloadRetiradaRegistrada(
        UUID entregaId,
        String fotoBase64,
        String nomeArquivo,
        List<FotoOffline> avarias,
        String observacaoAvaria,
        String dataHora,
        Double latitude,
        Double longitude) {
}
