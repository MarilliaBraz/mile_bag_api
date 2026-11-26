package com.marillia.milebag.business.encaminhamento.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Painel de gestao da companhia aerea (fase 1): relogio regulatorio da ANAC, tempos (localizacao x entrega),
 * resolucao em 24 h, cadeia de custodia, aeroportos e a lista do que agir agora.
 */
public record PainelCompanhiaGestaoResponse(
        Instant geradoEm,
        Regulatorio regulatorio,
        Tempos tempos,
        Custodia custodia,
        List<Aeroporto> aeroportos,
        List<ItemAgir> agirAgora) {

    /** Processos em aberto pelo prazo da ANAC: no prazo, em atencao (2 dias ou menos) e com o prazo estourado. */
    public record Regulatorio(long noPrazo, long atencao, long estourados) {
    }

    /**
     * Ultimos 30 dias (entregues): localizacao = da abertura do processo ao registro da mala no aeroporto (gargalo da
     * companhia); entrega = do encaminhamento a porta (gargalo do parceiro); resolucao em 24 h, em %. Nulos sem dado.
     */
    public record Tempos(Long localizacaoMinutos, Long entregaMinutos, Double resolucao24h, long entregues30Dias) {
    }

    /** Quem esta com a mala agora. */
    public record Custodia(long naSala, long aguardandoColeta, long emRota, long entreguesHoje, long naoEntregues) {
    }

    public record Aeroporto(String codigo, long naSala, long aguardandoColeta, long emRota, long naoEntregues, long estourados,
            Long entregaMinutos) {
    }

    /** ESTOURADO, NAO_ENTREGUE, ATENCAO (prazo da ANAC perto) ou PARADA_NA_SALA (mais de 24 h sem encaminhar). */
    public record ItemAgir(UUID bagagemId, String motivo, String processo, String passageiro, String aeroporto, String empresa,
            Instant referencia) {
    }
}
