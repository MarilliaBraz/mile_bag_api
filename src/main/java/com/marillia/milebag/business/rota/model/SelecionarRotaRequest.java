package com.marillia.milebag.business.rota.model;

import java.util.List;

/**
 * {@code alternativaId}: obrigatorio quando {@code tipoSelecao = SUGERIDA} -
 * id de uma das alternativas devolvidas pelo calculo anterior.
 *
 * <p>{@code tracadoManual}/{@code justificativa}: obrigatorios quando
 * {@code tipoSelecao = PROPRIA} (FR-004/FR-005).</p>
 */
public record SelecionarRotaRequest(
        TipoSelecao tipoSelecao, String alternativaId, List<Coordenada> tracadoManual, String justificativa) {
}
