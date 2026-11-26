package com.marillia.milebag.business.painelgestao.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Painel de gestao, fase 3: custo por mala, ocupacao das saidas e a situacao dos motoristas. */
public record PainelFrotaResponse(Instant geradoEm, Custo custoHoje, Custo custo30Dias, Ocupacao ocupacao30Dias, List<Praca> pracas,
        List<MotoristaStatus> motoristas) {

    /**
     * Custo das viagens encerradas no periodo (pagamento ao motorista + pedagio, inclusive das tentativas sem sucesso)
     * dividido pelas malas entregues. {@code viagensSemValor}: viagens cuja tarifa ou pedagio ainda nao se resolve.
     */
    public record Custo(BigDecimal total, BigDecimal pagamentoMotoristas, BigDecimal pedagios, long viagens, long viagensSemValor,
            long malasEntregues, BigDecimal porMala, double km, BigDecimal porKm) {
    }

    /** Malas por saida: quantas o motorista leva em cada viagem (a do BDO mais as caronas). */
    public record Ocupacao(long saidas, Double malasPorSaida, long saidasComUmaMala) {
    }

    /** EM_ROTA (viagem em andamento), DISPONIVEL (usou o app nos ultimos 15 min), OFFLINE. */
    public enum SituacaoMotorista { EM_ROTA, DISPONIVEL, OFFLINE }

    public record Praca(String base, long emRota, long disponiveis, long offline) {
    }

    /** Pontualidade e primeira tentativa em %, dos ultimos 30 dias; nulas sem entregas. */
    public record MotoristaStatus(UUID id, String nome, String base, SituacaoMotorista situacao, Instant ultimoAcesso, long entregas,
            Double pontualidade, Double primeiraTentativa, Double malasPorSaida) {
    }
}
