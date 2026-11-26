package com.marillia.milebag.core.base;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Le as colunas de uma linha de consulta nativa, aceitando os tipos que o driver/Hibernate devolvem. */
public final class LinhaNativa {

    private final Object[] colunas;

    public LinhaNativa(Object[] colunas) {
        this.colunas = colunas;
    }

    public UUID uuid(int i) {
        Object valor = colunas[i];
        return valor == null ? null : valor instanceof UUID id ? id : UUID.fromString(valor.toString());
    }

    public String texto(int i) {
        return colunas[i] == null ? null : colunas[i].toString();
    }

    public Instant instante(int i) {
        return switch (colunas[i]) {
            case null -> null;
            case Instant valor -> valor;
            case OffsetDateTime valor -> valor.toInstant();
            case ZonedDateTime valor -> valor.toInstant();
            case Timestamp valor -> valor.toInstant();
            default -> throw new IllegalStateException("Coluna " + i + " nao e data/hora: " + colunas[i].getClass());
        };
    }

    public Double decimal(int i) {
        return colunas[i] == null ? null : ((Number) colunas[i]).doubleValue();
    }

    public long numero(int i) {
        return colunas[i] == null ? 0 : ((Number) colunas[i]).longValue();
    }

    public boolean logico(int i) {
        return colunas[i] != null && (Boolean) colunas[i];
    }
}
