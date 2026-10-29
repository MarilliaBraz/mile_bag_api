package com.marillia.milebag.business.bagagemencontrada.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Os sinais particulares numa coluna so, separados por virgula (V43): {@code FITA,CADEADO}. */
@Converter
public class SinaisParticularesConverter implements AttributeConverter<Set<SinalParticular>, String> {

    @Override
    public String convertToDatabaseColumn(Set<SinalParticular> sinais) {
        return sinais == null || sinais.isEmpty() ? null : sinais.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<SinalParticular> convertToEntityAttribute(String coluna) {
        if (coluna == null || coluna.isBlank()) {
            return EnumSet.noneOf(SinalParticular.class);
        }
        return Arrays.stream(coluna.split(",")).map(SinalParticular::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(SinalParticular.class)));
    }
}
