package com.marillia.milebag.business.viagem.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Item 9: lote de pontos gravados pelo app durante a viagem (inclusive sem internet, enviados depois). */
public record RegistrarPosicoesViagemRequest(List<Posicao> posicoes) {

    /** {@code entregaId}: a entrega em viagem quando o ponto foi lido; {@code dataHora}: hora no celular. */
    public record Posicao(String idLocal, UUID entregaId, Double latitude, Double longitude, Double precisaoMetros, Instant dataHora) {
    }
}
