package com.marillia.milebag.business.pedagio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ver contracts/pedagios-api.md. {@code valorVigente}/{@code vigenteDesde}
 * ficam nulos quando a praca so tem valores com vigencia futura (nenhum
 * ainda vigente hoje). {@code latitude}/{@code longitude} (HU24) ficam nulos
 * enquanto a praca nao tem localizacao (nao e detectada nas rotas).
 */
public record PracaPedagioResponse(UUID id, String nome, BigDecimal valorVigente, LocalDate vigenteDesde, Double latitude, Double longitude) {

    public static PracaPedagioResponse de(PracaPedagio praca, PracaPedagioValor valorVigente) {
        return new PracaPedagioResponse(
                praca.getId(),
                praca.getNome(),
                valorVigente == null ? null : valorVigente.getValor(),
                valorVigente == null ? null : valorVigente.getVigenteDesde(),
                praca.getLatitude(),
                praca.getLongitude());
    }
}
