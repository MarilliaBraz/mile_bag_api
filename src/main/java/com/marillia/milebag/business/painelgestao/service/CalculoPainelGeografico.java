package com.marillia.milebag.business.painelgestao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.marillia.milebag.business.painelgestao.model.BaseCadastrada;
import com.marillia.milebag.business.painelgestao.model.MalaAberta;
import com.marillia.milebag.business.painelgestao.model.MalaEntregue;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse.BaseOperacao;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse.RotaAtiva;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse.SituacaoBase;
import com.marillia.milebag.business.painelgestao.model.PainelGeograficoResponse.SituacaoRota;
import com.marillia.milebag.business.painelgestao.model.ViagemAtiva;

/** Regras da fase 2 (bases e rotas) sobre o que ja foi lido: puro, sem banco. */
final class CalculoPainelGeografico {

    /** Mala na base sem coleta por mais que isto: retencao (amarelo). O mesmo prazo de 1 h para um motorista assumir. */
    static final Duration RETENCAO = Duration.ofHours(1);
    static final int TAMANHO_RANKING = 5;
    /** Rota longa: fora da regiao metropolitana (a partir desta distancia de ida) ou com pedagio. */
    static final double KM_ROTA_LONGA = 50;
    /** Janela para dizer se o motorista esta parado ou sem sinal. */
    static final int MINUTOS_PARADO = 20;
    /** Deslocamento menor que isto na janela acima: parado. */
    static final double METROS_PARADO = 300;
    /** Desvio: rodou mais que a rota prevista vezes este fator, mais a folga. */
    static final double FATOR_DESVIO = 1.3;
    static final double FOLGA_DESVIO_KM = 2;
    static final String SEM_BASE = "";

    private CalculoPainelGeografico() {
    }

    static PainelGeograficoResponse calcular(List<BaseCadastrada> cadastradas, List<MalaAberta> abertas, List<MalaEntregue> entregues,
            List<ViagemAtiva> viagens, Instant agora, Instant inicioHoje) {
        Map<String, BaseCadastrada> porCodigo = new LinkedHashMap<>();
        cadastradas.forEach(base -> porCodigo.put(base.codigo(), base));
        // Malas de uma base que nao esta (mais) no cadastro tambem aparecem, sem posicao no mapa.
        Stream.concat(abertas.stream().map(MalaAberta::base), entregues.stream().map(MalaEntregue::base))
                .map(codigo -> codigo == null ? SEM_BASE : codigo)
                .forEach(codigo -> porCodigo.putIfAbsent(codigo, new BaseCadastrada(codigo, null, null, null, null)));
        Map<String, List<MalaAberta>> abertasPorBase = abertas.stream().collect(Collectors.groupingBy(m -> chave(m.base())));
        Map<String, List<Instant[]>> coletasHoje = coletasDoDia(abertas, entregues, inicioHoje);
        List<BaseOperacao> bases = porCodigo.values().stream()
                .map(base -> base(base, abertasPorBase.getOrDefault(base.codigo(), List.of()), coletasHoje.getOrDefault(base.codigo(), List.of()), agora))
                .toList();
        return new PainelGeograficoResponse(agora, bases, ranking(bases), viagens.stream().map(v -> rota(v, agora)).toList());
    }

    private static String chave(String base) {
        return base == null ? SEM_BASE : base;
    }

    /** Coletas (retiradas) feitas hoje, por base: [registro do BDO, retirada]. */
    private static Map<String, List<Instant[]>> coletasDoDia(List<MalaAberta> abertas, List<MalaEntregue> entregues, Instant inicioHoje) {
        return Stream.concat(
                        abertas.stream().map(m -> Map.entry(chave(m.base()), new Instant[] {m.registradoEm(), m.retiradaEm()})),
                        entregues.stream().map(m -> Map.entry(chave(m.base()), new Instant[] {m.registradoEm(), m.retiradaEm()})))
                .filter(par -> par.getValue()[1] != null && !par.getValue()[1].isBefore(inicioHoje))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static BaseOperacao base(BaseCadastrada base, List<MalaAberta> abertas, List<Instant[]> coletas, Instant agora) {
        List<MalaAberta> aguardando = abertas.stream().filter(m -> m.retiradaEm() == null).toList();
        List<Long> esperas = aguardando.stream()
                .filter(m -> m.registradoEm() != null)
                .map(m -> Duration.between(m.registradoEm(), agora).toMinutes())
                .toList();
        long retidas = esperas.stream().filter(minutos -> minutos > RETENCAO.toMinutes()).count();
        long atrasadas = abertas.stream().filter(m -> m.prazo() != null && m.prazo().isBefore(agora)).count();
        long emAlerta = abertas.stream()
                .filter(m -> m.prazo() != null && !m.prazo().isBefore(agora) && m.prazo().isBefore(agora.plus(CalculoPainelGestao.ALERTA)))
                .count();
        List<Long> tempoColeta = coletas.stream()
                .filter(par -> par[0] != null && par[0].isBefore(par[1]))
                .map(par -> Duration.between(par[0], par[1]).toMinutes())
                .toList();
        SituacaoBase situacao = atrasadas > 0 ? SituacaoBase.CRITICO : retidas > 0 || emAlerta > 0 ? SituacaoBase.RETENCAO : SituacaoBase.NORMAL;
        return new BaseOperacao(base.codigo(), base.nome(), base.cidade(), base.latitude(), base.longitude(), situacao, aguardando.size(),
                retidas, media(esperas), esperas.stream().max(Long::compare).orElse(null), emAlerta, atrasadas, coletas.size(), media(tempoColeta));
    }

    /** As piores do dia: maior espera media das malas paradas na base, depois o volume represado, depois a coleta mais lenta hoje. */
    private static List<BaseOperacao> ranking(List<BaseOperacao> bases) {
        Comparator<Long> maiorPrimeiro = Comparator.nullsLast(Comparator.<Long>reverseOrder());
        return bases.stream()
                .filter(base -> base.aguardandoColeta() > 0 || base.retiradasHoje() > 0)
                .sorted(Comparator.comparing(BaseOperacao::esperaMediaMinutos, maiorPrimeiro)
                        .thenComparing(BaseOperacao::aguardandoColeta, Comparator.reverseOrder())
                        .thenComparing(BaseOperacao::coletaMediaHojeMinutos, maiorPrimeiro))
                .limit(TAMANHO_RANKING)
                .toList();
    }

    static RotaAtiva rota(ViagemAtiva v, Instant agora) {
        Double kmPrevistos = v.metrosPrevistos() == null ? null : v.metrosPrevistos() / 1000;
        double kmPercorridos = v.metrosPercorridos() / 1000;
        boolean longa = v.pedagio() || (kmPrevistos != null && kmPrevistos >= KM_ROTA_LONGA);
        String destino = Stream.of(v.cidade(), v.uf()).filter(Objects::nonNull).collect(Collectors.joining("/"));
        return new RotaAtiva(v.trajetoId(), v.entregaId(), v.numero(), v.motorista(), v.base(), destino.isEmpty() ? null : destino, v.inicio(),
                kmPrevistos, v.pedagio(), v.pracas(), v.entregue(), Math.round(kmPercorridos * 10) / 10.0, v.ultimaPosicao(), v.latitude(),
                v.longitude(), longa, situacao(v, kmPrevistos, kmPercorridos, agora));
    }

    private static SituacaoRota situacao(ViagemAtiva v, Double kmPrevistos, double kmPercorridos, Instant agora) {
        Instant janela = agora.minus(Duration.ofMinutes(MINUTOS_PARADO));
        // Viagem recem-iniciada ainda nao tem como estar parada nem sem sinal.
        if (v.inicio() != null && v.inicio().isAfter(janela)) {
            return SituacaoRota.NORMAL;
        }
        if (kmPrevistos != null) {
            // Depois da entrega, o motorista volta para a base: o previsto dobra.
            double limite = kmPrevistos * (v.entregue() ? 2 : 1) * FATOR_DESVIO + FOLGA_DESVIO_KM;
            if (kmPercorridos > limite) {
                return SituacaoRota.DESVIO;
            }
        }
        if (v.ultimaPosicao() == null || v.ultimaPosicao().isBefore(janela)) {
            return SituacaoRota.SEM_SINAL;
        }
        if (v.posicoesRecentes() >= 2 && v.deslocamentoRecenteMetros() != null && v.deslocamentoRecenteMetros() < METROS_PARADO) {
            return SituacaoRota.PARADO;
        }
        return SituacaoRota.NORMAL;
    }

    private static Long media(List<Long> valores) {
        return valores.isEmpty() ? null : Math.round(valores.stream().mapToLong(Long::longValue).average().orElse(0));
    }
}
