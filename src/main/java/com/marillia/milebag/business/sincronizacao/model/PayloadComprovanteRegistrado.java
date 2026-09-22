package com.marillia.milebag.business.sincronizacao.model;

import java.util.UUID;

/**
 * Adaptacao de {@code POST /comprovante} (multipart, HU06) para o lote
 * offline: foto em base64, {@code entregaId} no payload.
 */
public record PayloadComprovanteRegistrado(
        UUID entregaId,
        String fotoBase64,
        String nomeArquivo,
        String identificacaoRecebedor,
        String dataHora,
        Double latitude,
        Double longitude,
        String justificativaDivergencia) {
}
