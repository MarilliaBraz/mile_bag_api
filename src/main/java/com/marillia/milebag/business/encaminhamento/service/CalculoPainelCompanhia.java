package com.marillia.milebag.business.encaminhamento.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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
            Instant prazoAnac, Instant encaminhadoEm, Instant entregueEm, Instant naoEntregueEm, String aeroporto, Instant registradaEm,
            String empresa) {
    }

    record NaSala(UUID bagagemId, String aeroporto, Instant registradaEm, String identificacao, String passageiro) {
    }

    private CalculoPainelCompanhia() {
    }

    static PainelCompanhiaGestaoResponse calcular(List<Encaminhamento> encaminhamentos, List<NaSala> naSala, Instant agora, Instant inicioHoje) {
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
                agirAgora(abertos, naSala, agora, estourado, atencao));
    }

    private static PainelCompanhiaGestaoResponse.Tempos tempos(List<Encaminhamento> entregues) {
        Long localizacao = media(entregues.stream()
                .filter(e -> e.registradaEm() != null && e.processoAbertoEm().isBefore(e.registradaEm()))
                .map(e -> Duration.between(e.processoAbertoEm(), e.registradaEm())));
        Long entrega = media(entregues.stream().map(e -> Duration.between(e.encaminhadoEm(), e.entregueEm())));
        Double resolucao = entregues.isEmpty() ? null
                : Math.round(entregues.stream().filter(e -> !Duration.between(e.processoAbertoEm(), e.entregueEm()).minus(RESOLUCAO).isPositive()).count()
                        * 1000.0 / entregues.size()) / 10.0;
        return new PainelCompanhiaGestaoResponse.Tempos(localizacao, entrega, resolucao, entregues.size());
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
