package com.marillia.milebag.business.painelgestao.model;

import java.time.Instant;
import java.util.UUID;

/** Entrega concluida no periodo do painel, com o prazo, a retirada e as provas (fotos e recebedor). */
public record MalaEntregue(
        UUID bdoId,
        UUID entregaId,
        String numero,
        String passageiro,
        String base,
        String motorista,
        Instant entregueEm,
        Instant prazo,
        Instant retiradaEm,
        boolean fotoPorta,
        boolean fotoBdoAssinado,
        boolean recebedor,
        boolean avariaRetirada,
        boolean avariaEntrega) {
}
