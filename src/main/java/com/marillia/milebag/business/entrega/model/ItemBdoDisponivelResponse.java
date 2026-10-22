package com.marillia.milebag.business.entrega.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.marillia.milebag.business.bdo.model.BdoOrigem;
import com.marillia.milebag.business.bdo.model.PendenciaContatoBdo;

/**
 * Um BDO confirmado ainda sem motorista responsavel (HU19, FR-007) - ver
 * contracts/painel-api.md. {@code reaberta}: ja houve tentativa e ocorrencia
 * (RN05); {@code janelaReagendamento} so quando a ocorrencia foi de
 * reagendamento (HU29).
 */
public record ItemBdoDisponivelResponse(
        UUID bdoId,
        BdoOrigem origem,
        String bdoNumero,
        String passageiroNome,
        String endereco,
        String etiquetaBagagem,
        Instant dataCaptura,
        List<PendenciaContatoBdo> pendenciasContato,
        boolean reaberta,
        JanelaReagendamento janelaReagendamento) {
}
