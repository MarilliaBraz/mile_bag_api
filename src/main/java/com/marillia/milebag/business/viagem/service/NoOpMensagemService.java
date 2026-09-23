package com.marillia.milebag.business.viagem.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.marillia.milebag.business.viagem.model.TipoCheckpoint;

/**
 * Implementacao provisoria de {@link MensagemService}, ate HU18 (mensageria)
 * ser implementada e fornecer a classe real (research.md desta HU, item 2) -
 * unica implementacao do Spring encontra hoje, entao e injetada
 * automaticamente sem qualificador. Substituir por T011 do tasks.md desta HU
 * quando HU18 existir; nenhuma mudanca em {@code ViagemService} sera
 * necessaria nesse momento.
 */
@Service
public class NoOpMensagemService implements MensagemService {

    private static final Logger log = LoggerFactory.getLogger(NoOpMensagemService.class);

    @Override
    public void notificarAutomatica(UUID entregaId, TipoCheckpoint tipoEvento) {
        log.debug(
                "Notificacao automatica de {} para a entrega {} ainda nao disparada: MensagemService (HU18) nao implementado.",
                tipoEvento, entregaId);
    }
}
