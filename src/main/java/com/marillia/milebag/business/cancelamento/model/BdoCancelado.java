package com.marillia.milebag.business.cancelamento.model;

import java.util.UUID;

/**
 * Evento publicado quando a empresa cancela um BDO que estava com um motorista (V47) - o aviso por push sai depois do
 * commit (NotificadorCancelamento), so de um cancelamento que ficou registrado.
 */
public record BdoCancelado(UUID bdoId, String bdoNumero, UUID motoristaId, String motivo) {
}
