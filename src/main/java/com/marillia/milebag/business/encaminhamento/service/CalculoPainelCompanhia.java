package com.marillia.milebag.business.encaminhamento.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaGestaoResponse;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaGestaoResponse.Aeroporto;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaGestaoResponse.ItemAgir;
import com.marillia.milebag.business.encaminhamento.model.TipoCusto;

/** Regras do painel de gestao da companhia (fase 1) sobre o que ja foi lido: puro, sem banco. */
final class CalculoPainelCompanhia {

    /** Faltando isto ou menos para o prazo da ANAC: atencao. */
    static final Duration ATENCAO = Duration.ofDays(2);
    static final Duration RESOLUCAO = Duration.ofHours(24);
    /** Mala registrada e sem encaminhar ha mais que isto: parada na sala. */
    static final Duration PARADA_NA_SALA = Duration.ofHours(24);
    static final String SEM_AEROPORTO = "";

    static final Set<EtapaEntrega> AGUARDANDO_COLETA = Set.of(EtapaEntrega.AGUARDANDO_MOTORISTA, EtapaEntrega.MOTORISTA_DESIGNADO);
    static final Set<EtapaEntrega> EM_ABERTO =
            Set.of(EtapaEntrega.AGUARDANDO_MOTORISTA, EtapaEntrega.MOTORISTA_DESIGNADO, EtapaEntrega.A_CAMINHO, EtapaEntrega.NAO_ENTREGUE);

    /** Encaminhamento lido do banco, com o prazo da ANAC ja resolvido. */
    record Encaminhamento(UUID bagagemId, String processo, String passageiro, EtapaEntrega etapa, Instant processoAbertoEm,
            Instant prazoAnac, Instant encaminhadoEm, Instant coletadoEm, Instant entregueEm, Instant naoEntregueEm, String aeroporto, Instant registradaEm,
            String empresa) {
    }

    /** Fase 3: um custo lancado no periodo. */
    record Custo(UUID bagagemId, TipoCusto tipo, BigDecimal valor) {
    }

    /** Fase 3: uma resposta da pesquisa de satisfacao. */
    record Avaliacao(int nota, Integer cordialidade, Boolean malaEmBomEstado) {
    }

    record NaSala(UUID bagagemId, String aeroporto, Instant registradaEm, String identificacao, String passageiro) {
    }

    private CalculoPainelCompanhia() {
    }

    static PainelCompanhiaGestaoResponse calcular(List<Encaminhamento> encaminhamentos, List<NaSala> naSala, Instant agora, Instant inicioHoje) {
        return calcular(encaminhamentos, naSala, List.of(), List.of(), agora, inicioHoje);
    }

    static PainelCompanhiaGestaoResponse calcular(List<Encaminhamento> encaminhamentos, List<NaSala> naSala, List<Custo> custos,
            List<Avaliacao> avaliacoes, Instant agora, Instant inicioHoje) {
        List<Encaminhamento> abertos = encaminhamentos.stream().filter(e -> EM_ABERTO.contains(e.etapa())).toList();
        List<Encaminhamento> entregues = encaminhamentos.stream().filter(e -> e.etapa() == EtapaEntrega.ENTREGUE && e.entregueEm() != null).toList();
        Predicate<Encaminhamento> estourado = e -> e.prazoAnac().isBefore(agora);
        Predicate<Encaminhamento> atencao = e -> !estourado.test(e) && !e.prazoAnac().isAfter(agora.plus(ATENCAO));
        return new PainelCompanhiaGestaoResponse(
                agora,
                new PainelCompanhiaGestaoResponse.Regulatorio(
                        abertos.stream().filter(estourado.negate().and(atencao.negate())).count(),
                        abertos.stream().filter(atencao).count(),
                        abertos.stream().filter(estourado).count()),
                tempos(entregues),
                new PainelCompanhiaGestaoResponse.Custodia(
                        naSala.size(),
                        abertos.stream().filter(e -> AGUARDANDO_COLETA.contains(e.etapa())).count(),
                        abertos.stream().filter(e -> e.etapa() == EtapaEntrega.A_CAMINHO).count(),
                        entregues.stream().filter(e -> !e.entregueEm().isBefore(inicioHoje)).count(),
                        abertos.stream().filter(e -> e.etapa() == EtapaEntrega.NAO_ENTREGUE).count()),
                aeroportos(abertos, entregues, naSala, estourado),
                agirAgora(abertos, naSala, agora, estourado, atencao),
                parceiros(abertos, entregues),
                financeiro(encaminhamentos, entregues, custos),
                satisfacao(avaliacoes));
    }

    /** Custos dos ultimos 30 dias: total, por tipo e por processo; e a media dos resolvidos em ate 24 h contra os demais. */
    private static PainelCompanhiaGestaoResponse.Financeiro financeiro(List<Encaminhamento> encaminhamentos, List<Encaminhamento> entregues,
            List<Custo> custos) {
        BigDecimal total = custos.stream().map(Custo::valor).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        Map<TipoCusto, BigDecimal> porTipo = new EnumMap<>(TipoCusto.class);
        custos.forEach(c -> porTipo.merge(c.tipo(), c.valor(), BigDecimal::add));
        Map<UUID, BigDecimal> porBagagem = custos.stream()
                .collect(Collectors.toMap(Custo::bagagemId, Custo::valor, BigDecimal::add));
        long processos = encaminhamentos.stream().map(Encaminhamento::bagagemId).distinct().count();
        Predicate<Encaminhamento> em24h = e -> !Duration.between(e.processoAbertoEm(), e.entregueEm()).minus(RESOLUCAO).isPositive();
        return new PainelCompanhiaGestaoResponse.Financeiro(total, porTipo, processos, dividir(total, processos),
                mediaDeCusto(entregues.stream().filter(em24h), porBagagem), mediaDeCusto(entregues.stream().filter(em24h.negate()), porBagagem));
    }

    /** Media do custo lancado por processo do grupo; so conta processo com algum custo lancado. */
    private static BigDecimal mediaDeCusto(Stream<Encaminhamento> grupo, Map<UUID, BigDecimal> porBagagem) {
        List<BigDecimal> valores = grupo.map(e -> porBagagem.get(e.bagagemId())).filter(Objects::nonNull).toList();
        return dividir(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add), valores.size());
    }

    private static BigDecimal dividir(BigDecimal valor, long quantidade) {
        return quantidade == 0 ? null : valor.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    /** NPS = % de promotores (9 e 10) menos % de detratores (0 a 6). */
    private static PainelCompanhiaGestaoResponse.Satisfacao satisfacao(List<Avaliacao> avaliacoes) {
        if (avaliacoes.isEmpty()) {
            return new PainelCompanhiaGestaoResponse.Satisfacao(0, null, null, null, 0);
        }
        long promotores = avaliacoes.stream().filter(a -> a.nota() >= 9).count();
        long detratores = avaliacoes.stream().filter(a -> a.nota() <= 6).count();
        double nps = Math.round((promotores - detratores) * 1000.0 / avaliacoes.size()) / 10.0;
        double notaMedia = Math.round(avaliacoes.stream().mapToInt(Avaliacao::nota).average().orElse(0) * 10) / 10.0;
        Double cordialidade = avaliacoes.stream().map(Avaliacao::cordialidade).filter(Objects::nonNull).mapToInt(Integer::intValue).average()
                .stream().map(media -> Math.round(media * 10) / 10.0).boxed().findFirst().orElse(null);
        long danificadas = avaliacoes.stream().filter(a -> Boolean.FALSE.equals(a.malaEmBomEstado())).count();
        return new PainelCompanhiaGestaoResponse.Satisfacao(avaliacoes.size(), nps, notaMedia, cordialidade, danificadas);
    }

    /** SLA por empresa de entrega e aeroporto; o pior tempo de entrega primeiro. */
    private static List<PainelCompanhiaGestaoResponse.Parceiro> parceiros(List<Encaminhamento> abertos, List<Encaminhamento> entregues) {
        Map<List<String>, List<Encaminhamento>> todos = Stream.concat(abertos.stream(), entregues.stream())
                .collect(Collectors.groupingBy(e -> List.of(Objects.requireNonNullElse(e.empresa(), ""), chave(e.aeroporto()))));
        return todos.entrySet().stream()
                .map(grupo -> {
                    List<Encaminhamento> doGrupo = grupo.getValue();
                    List<Encaminhamento> entreguesDoGrupo = doGrupo.stream().filter(e -> e.etapa() == EtapaEntrega.ENTREGUE).toList();
                    return new PainelCompanhiaGestaoResponse.Parceiro(grupo.getKey().get(0), grupo.getKey().get(1), entreguesDoGrupo.size(),
                            media(doGrupo.stream().filter(e -> e.coletadoEm() != null).map(e -> Duration.between(e.encaminhadoEm(), e.coletadoEm()))),
                            media(entreguesDoGrupo.stream().map(e -> Duration.between(e.encaminhadoEm(), e.entregueEm()))),
                            resolucao(entreguesDoGrupo),
                            doGrupo.stream().filter(e -> e.etapa() == EtapaEntrega.NAO_ENTREGUE).count(),
                            doGrupo.stream().filter(e -> EM_ABERTO.contains(e.etapa())).count());
                })
                .sorted(Comparator.comparing(PainelCompanhiaGestaoResponse.Parceiro::entregaMinutos, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PainelCompanhiaGestaoResponse.Parceiro::naoEntregues, Comparator.reverseOrder()))
                .toList();
    }

    private static Double resolucao(List<Encaminhamento> entregues) {
        return entregues.isEmpty() ? null
                : Math.round(entregues.stream().filter(e -> !Duration.between(e.processoAbertoEm(), e.entregueEm()).minus(RESOLUCAO).isPositive()).count()
                        * 1000.0 / entregues.size()) / 10.0;
    }

    private static PainelCompanhiaGestaoResponse.Tempos tempos(List<Encaminhamento> entregues) {
        Long localizacao = media(entregues.stream()
                .filter(e -> e.registradaEm() != null && e.processoAbertoEm().isBefore(e.registradaEm()))
                .map(e -> Duration.between(e.processoAbertoEm(), e.registradaEm())));
        Long entrega = media(entregues.stream().map(e -> Duration.between(e.encaminhadoEm(), e.entregueEm())));
        return new PainelCompanhiaGestaoResponse.Tempos(localizacao, entrega, resolucao(entregues), entregues.size());
    }

    private static List<Aeroporto> aeroportos(List<Encaminhamento> abertos, List<Encaminhamento> entregues, List<NaSala> naSala,
            Predicate<Encaminhamento> estourado) {
        Map<String, List<Encaminhamento>> abertosPor = abertos.stream().collect(Collectors.groupingBy(e -> chave(e.aeroporto())));
        Map<String, List<Encaminhamento>> entreguesPor = entregues.stream().collect(Collectors.groupingBy(e -> chave(e.aeroporto())));
        Map<String, Long> salaPor = naSala.stream().collect(Collectors.groupingBy(b -> chave(b.aeroporto()), Collectors.counting()));
        return Stream.of(abertosPor.keySet(), entreguesPor.keySet(), salaPor.keySet())
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .map(codigo -> {
                    List<Encaminhamento> doAeroporto = abertosPor.getOrDefault(codigo, List.of());
                    return new Aeroporto(codigo, salaPor.getOrDefault(codigo, 0L),
                            doAeroporto.stream().filter(e -> AGUARDANDO_COLETA.contains(e.etapa())).count(),
                            doAeroporto.stream().filter(e -> e.etapa() == EtapaEntrega.A_CAMINHO).count(),
                            doAeroporto.stream().filter(e -> e.etapa() == EtapaEntrega.NAO_ENTREGUE).count(),
                            doAeroporto.stream().filter(estourado).count(),
                            media(entreguesPor.getOrDefault(codigo, List.of()).stream().map(e -> Duration.between(e.encaminhadoEm(), e.entregueEm()))));
                })
                .sorted(Comparator.comparing(Aeroporto::estourados).thenComparing(Aeroporto::naoEntregues).reversed())
                .toList();
    }

    /** O mais grave primeiro: prazo da ANAC estourado, nao entregue, prazo perto e mala parada na sala. */
    private static List<ItemAgir> agirAgora(List<Encaminhamento> abertos, List<NaSala> naSala, Instant agora, Predicate<Encaminhamento> estourado,
            Predicate<Encaminhamento> atencao) {
        Comparator<Encaminhamento> pelaData = Comparator.comparing(Encaminhamento::prazoAnac);
        return Stream.of(
                        abertos.stream().filter(estourado).sorted(pelaData).map(e -> item(e, "ESTOURADO", e.prazoAnac())),
                        abertos.stream().filter(estourado.negate()).filter(e -> e.etapa() == EtapaEntrega.NAO_ENTREGUE)
                                .map(e -> item(e, "NAO_ENTREGUE", Objects.requireNonNullElse(e.naoEntregueEm(), e.encaminhadoEm()))),
                        abertos.stream().filter(atencao).filter(e -> e.etapa() != EtapaEntrega.NAO_ENTREGUE).sorted(pelaData)
                                .map(e -> item(e, "ATENCAO", e.prazoAnac())),
                        naSala.stream()
                                .filter(b -> b.registradaEm() != null && b.registradaEm().isBefore(agora.minus(PARADA_NA_SALA)))
                                .sorted(Comparator.comparing(NaSala::registradaEm))
                                .map(b -> new ItemAgir(b.bagagemId(), "PARADA_NA_SALA", b.identificacao(), b.passageiro(), b.aeroporto(), null,
                                        b.registradaEm())))
                .flatMap(s -> s)
                .limit(100)
                .toList();
    }

    private static ItemAgir item(Encaminhamento e, String motivo, Instant referencia) {
        return new ItemAgir(e.bagagemId(), motivo, e.processo(), e.passageiro(), e.aeroporto(), e.empresa(), referencia);
    }

    private static String chave(String aeroporto) {
        return aeroporto == null ? SEM_AEROPORTO : aeroporto;
    }

    private static Long media(Stream<Duration> duracoes) {
        List<Long> minutos = duracoes.filter(d -> !d.isNegative()).map(Duration::toMinutes).toList();
        return minutos.isEmpty() ? null : Math.round(minutos.stream().mapToLong(Long::longValue).average().orElse(0));
    }
}
