package com.marillia.milebag.business.painelgestao.model;

import java.time.Instant;
import java.util.UUID;

/** Viagem em execucao com a rota escolhida e o resumo do GPS, como vem do banco. */
public record ViagemAtiva(
        UUID trajetoId,
        UUID entregaId,
        String numero,
        String motorista,
        String base,
        String cidade,
        String uf,
        Instant inicio,
        Double metrosPrevistos,
        boolean pedagio,
        long pracas,
        boolean entregue,
        double metrosPercorridos,
        Instant ultimaPosicao,
        Double latitude,
        Double longitude,
        long posicoesRecentes,
        Double deslocamentoRecenteMetros) {
}
