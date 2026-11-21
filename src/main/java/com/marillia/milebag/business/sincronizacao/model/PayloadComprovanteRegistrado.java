package com.marillia.milebag.business.sincronizacao.model;

import java.util.List;
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
        String justificativaDivergencia,
        // V48: ausentes nos eventos guardados por versoes antigas do app (formato antigo, aceito ate a data de corte).
        FotoOffline fotoPorta,
        UUID tipoRecebedorId,
        String nomeRecebedor,
        List<FotoOffline> avarias,
        String observacaoAvaria) {
}
