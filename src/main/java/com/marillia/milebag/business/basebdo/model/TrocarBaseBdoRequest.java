package com.marillia.milebag.business.basebdo.model;

import java.util.UUID;

/** Corpo da troca de base do BDO (V52): a base nova e o motivo (obrigatorio). */
public record TrocarBaseBdoRequest(UUID baseId, String motivo) {
}
