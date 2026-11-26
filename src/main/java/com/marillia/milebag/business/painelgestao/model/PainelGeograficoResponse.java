package com.marillia.milebag.business.painelgestao.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Painel de gestao, fase 2: situacao de cada base (mapa), as 5 piores do dia e as viagens em andamento. */
public record PainelGeograficoResponse(Instant geradoEm, List<BaseOperacao> bases, List<BaseOperacao> ranking, List<RotaAtiva> rotas) {

    /** NORMAL (verde), RETENCAO (amarelo: mala esperando coleta ha mais de 1 h ou perto do prazo), CRITICO (vermelho: atrasada). */
    public enum SituacaoBase { NORMAL, RETENCAO, CRITICO }

    public record BaseOperacao(
            String codigo,
            String nome,
            String cidade,
            Double latitude,
            Double longitude,
            SituacaoBase situacao,
            long aguardandoColeta,
            long retidas,
            Long esperaMediaMinutos,
            Long maiorEsperaMinutos,
            long emAlerta,
            long atrasadas,
            long retiradasHoje,
            Long coletaMediaHojeMinutos) {
    }

    /** NORMAL; PARADO (sem sair do lugar nos ultimos minutos); SEM_SINAL (sem posicao nos ultimos minutos); DESVIO (rodou bem mais que a rota). */
    public enum SituacaoRota { NORMAL, PARADO, SEM_SINAL, DESVIO }

    public record RotaAtiva(
            UUID trajetoId,
            UUID entregaId,
            String numero,
            String motorista,
            String base,
            String destino,
            Instant inicio,
            Double kmPrevistos,
            boolean pedagio,
            long pracas,
            boolean entregue,
            double kmPercorridos,
            Instant ultimaPosicao,
            Double latitude,
            Double longitude,
            boolean longa,
            SituacaoRota situacao) {
    }
}
