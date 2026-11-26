package com.marillia.milebag.business.painelgestao.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Le as colunas de uma linha de consulta nativa, aceitando os tipos que o driver/Hibernate devolvem. */
final class LinhaNativa {

    private final Object[] colunas;

    LinhaNativa(Object[] colunas) {
        this.colunas = colunas;
    }

    UUID uuid(int i) {
        Object valor = colunas[i];
        return valor == null ? null : valor instanceof UUID id ? id : UUID.fromString(valor.toString());
    }

    String texto(int i) {
        return colunas[i] == null ? null : colunas[i].toString();
    }

    Instant instante(int i) {
        return switch (colunas[i]) {
            case null -> null;
            case Instant valor -> valor;
            case OffsetDateTime valor -> valor.toInstant();
            case ZonedDateTime valor -> valor.toInstant();
            case Timestamp valor -> valor.toInstant();
            default -> throw new IllegalStateException("Coluna " + i + " nao e data/hora: " + colunas[i].getClass());
        };
    }

    Double decimal(int i) {
        return colunas[i] == null ? null : ((Number) colunas[i]).doubleValue();
    }

    long numero(int i) {
        return colunas[i] == null ? 0 : ((Number) colunas[i]).longValue();
    }

    boolean logico(int i) {
        return colunas[i] != null && (Boolean) colunas[i];
    }
}
