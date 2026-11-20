package com.marillia.milebag.core.util;

import com.marillia.milebag.core.exception.RegraNegocioException;

/** Texto livre opcional digitado pelo usuario (complemento de dispensa, de cancelamento): aparado, vazio vira nulo, com limite. */
public final class TextoLivre {

    private TextoLivre() {
    }

    public static String opcional(String texto, int maximo, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.trim();
        if (limpo.length() > maximo) {
            throw new RegraNegocioException("O " + campo + " pode ter no maximo " + maximo + " caracteres.");
        }
        return limpo;
    }
}
