package com.marillia.milebag.business.correcao.model;

import java.util.UUID;

/**
 * Uma correcao feita no aparelho durante a entrega, antes de finalizar (V51) - vem com o comprovante, inclusive pela
 * fila offline. {@code dataHora} em ISO-8601, como os demais registros do aparelho.
 */
public record CorrecaoInformada(EtapaCorrecao etapa, UUID motivoId, String complemento, String antes, String depois, String dataHora) {
}
