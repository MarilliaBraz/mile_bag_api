package com.marillia.milebag.business.encaminhamento.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Painel da companhia, fase 2: a entrega do parceiro de uma bagagem encaminhada. Comprovante (POD) com as fotos, quem
 * recebeu, local e hora; a ultima tentativa sem sucesso; o contato do passageiro e o link de rastreio. Sem o motorista.
 */
public record EntregaParceiroResponse(
        EtapaEntrega etapa,
        String empresa,
        Instant encaminhadoEm,
        Instant coletadoEm,
        Instant entregueEm,
        Instant naoEntregueEm,
        Comprovante comprovante,
        Tentativa ultimaTentativa,
        String telefonePassageiro,
        String emailPassageiro,
        String linkAcompanhamento,
        List<Foto> fotos) {

    public record Comprovante(Instant dataHora, String nomeRecebedor, String tipoRecebedor, Double latitude, Double longitude) {
    }

    public record Tentativa(Instant dataHora, String motivo, String observacao) {
    }

    /** Tipos de TipoFotoEntrega; o BDO assinado tem o id do comprovante. */
    public record Foto(UUID id, String tipo, Instant dataHora) {
    }
}
