package com.marillia.milebag.business.bdo.model;

import java.util.UUID;

/**
 * A empresa incluiu um BDO pelo painel (V49). Publicado por {@code BdoService.criarPelaEmpresa}; o aviso aos
 * motoristas escuta depois do commit, como no BDO que chega por e-mail.
 */
public record BdoIncluidoPelaEmpresa(UUID bdoId, String numero) {
}
