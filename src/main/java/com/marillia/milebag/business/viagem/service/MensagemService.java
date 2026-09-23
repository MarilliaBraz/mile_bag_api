package com.marillia.milebag.business.viagem.service;

import java.util.UUID;

import com.marillia.milebag.business.viagem.model.TipoCheckpoint;

/**
 * Interface de integracao com a mensageria da entrega (HU20, FR-005) -
 * definida aqui (consumidor, {@code ViagemService.registrarCheckpoint}) e
 * nao em um pacote {@code business.mensageria}, que ainda nao existe (HU18).
 * Mesmo padrao de dependency inversion ja usado por {@link
 * SelecaoRotaProvider}: quando HU18 for implementada, a classe real de
 * mensageria passa a implementar esta interface, sem exigir nenhuma mudanca
 * em {@code ViagemService} (research.md desta HU, item 2).
 */
public interface MensagemService {

    /** No-op quando a entrega nao tem contato de passageiro disponivel (RD02) - nunca lanca excecao por isso. */
    void notificarAutomatica(UUID entregaId, TipoCheckpoint tipoEvento);
}
