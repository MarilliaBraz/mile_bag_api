package com.marillia.milebag.core.geo;

import java.util.List;

/**
 * Uma opcao de trajeto (FR-001/FR-002 da HU03). {@code possuiPedagio} junta duas fontes: o indicador agregado que
 * o proprio motor de rotas (Valhalla) devolve para o trajeto inteiro e as {@code pracasPedagio} cadastradas (HU10)
 * por onde o traçado passa (HU24) - so estas ultimas dizem quanto custa. Em {@code core} (nao em
 * {@code business.rota}) porque e reaproveitado por mais de um dominio (rota, viagem, entrega.bagagemcarona,
 * sincronizacao) sem carregar regra de negocio propria.
 *
 * <p>{@code pracasPedagio} nunca e nulo (propostas gravadas antes da HU24 nao tinham o campo).</p>
 */
public record RotaAlternativa(String id, double distanciaMetros, long duracaoSegundos, boolean possuiPedagio, List<PracaNaRota> pracasPedagio) {

    public RotaAlternativa {
        pracasPedagio = pracasPedagio == null ? List.of() : List.copyOf(pracasPedagio);
    }

    public RotaAlternativa(String id, double distanciaMetros, long duracaoSegundos, boolean possuiPedagio) {
        this(id, distanciaMetros, duracaoSegundos, possuiPedagio, List.of());
    }

    public RotaAlternativa comPracas(List<PracaNaRota> pracas) {
        return new RotaAlternativa(id, distanciaMetros, duracaoSegundos, possuiPedagio || !pracas.isEmpty(), pracas);
    }
}
