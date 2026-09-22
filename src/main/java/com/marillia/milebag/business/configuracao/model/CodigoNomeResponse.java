package com.marillia.milebag.business.configuracao.model;

import java.util.UUID;

/** Formato comum de resposta para base aeroportuaria e companhia aerea (mesmo formato nos dois endpoints do contrato). */
public record CodigoNomeResponse(UUID id, String codigoIata, String nome) {

    public static CodigoNomeResponse de(BaseAeroportuaria base) {
        return new CodigoNomeResponse(base.getId(), base.getCodigoIata(), base.getNome());
    }

    public static CodigoNomeResponse de(CompanhiaAereaAtendida companhia) {
        return new CodigoNomeResponse(companhia.getId(), companhia.getCodigoIata(), companhia.getNome());
    }
}
