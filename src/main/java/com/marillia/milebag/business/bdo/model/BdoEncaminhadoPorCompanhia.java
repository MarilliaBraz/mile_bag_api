package com.marillia.milebag.business.bdo.model;

import java.util.UUID;

/**
 * Evento de um BDO criado por uma companhia aerea na empresa parceira (HU37, RF30). Publicado por
 * {@code BdoService.criarPorCompanhia}; o aviso aos motoristas (HU32) escuta depois do commit. Leva o tenant da empresa
 * porque, ao contrario do e-mail, a requisicao e da companhia.
 */
public record BdoEncaminhadoPorCompanhia(UUID bdoId, String numero, UUID empresaId, String companhia) {
}
