package com.marillia.milebag.business.bdo.model;

import java.util.UUID;

/** A empresa passou o BDO para outra base (V52): os motoristas da base nova sao avisados depois do commit. */
public record BdoMudouDeBase(UUID bdoId, String numero) {
}
