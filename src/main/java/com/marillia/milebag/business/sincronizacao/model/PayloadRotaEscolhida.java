package com.marillia.milebag.business.sincronizacao.model;

import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.rota.model.Coordenada;
import com.marillia.milebag.business.rota.model.TipoSelecao;

/**
 * Adaptacao de {@code POST /rotas/selecionar} (HU03): {@code entregaId} era
 * path variable no endpoint sincrono, aqui vai no payload (pode ser o
 * {@code idLocal} de uma ENTREGA_AUTOATRIBUIDA anterior no mesmo lote).
 * Sem selecao {@code SUGERIDA} offline: sem rede nunca houve um
 * {@code /rotas/calcular} previo para gerar alternativas.
 */
public record PayloadRotaEscolhida(
        UUID entregaId, TipoSelecao tipoSelecao, String alternativaId, List<Coordenada> tracadoManual, String justificativa) {
}
