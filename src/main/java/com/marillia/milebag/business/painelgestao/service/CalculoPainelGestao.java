package com.marillia.milebag.business.painelgestao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.marillia.milebag.business.painelgestao.model.MalaAberta;
import com.marillia.milebag.business.painelgestao.model.MalaEntregue;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoResponse;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoResponse.ItemMala;

/** Regras do painel de gestao (fase 1) sobre as malas ja lidas: puro, sem banco, para testar cada numero. */
final class CalculoPainelGestao {

    /** Falta menos que isto para o prazo: alerta amarelo. */
    static final Duration ALERTA = Duration.ofHours(2);
    /** Cada lista do painel traz no maximo isto (os numeros continuam completos). */
    static final int LIMITE_LISTA = 100;
    static final String SEM_FOTO_PORTA = "SEM_FOTO_PORTA";
    static final String SEM_ASSINATURA = "SEM_ASSINATURA";
    static final String AVARIA_RETIRADA = "RETIRADA";
    static final String AVARIA_ENTREGA = "ENTREGA";
    static final String AVARIA_AMBAS = "AMBAS";

    /** Com o motorista: a mala ja foi atribuida ou retirada e pode se perder se ficar parada. */
    private static final Set<String> COM_MOTORISTA = Set.of("ATRIBUIDA", "EM_ANDAMENTO", "REABERTA");

    private CalculoPainelGestao() {
    }

    static PainelGestaoResponse calcular(List<MalaAberta> abertas, List<MalaEntregue> entregues30Dias, Instant agora, Instant inicioHoje,
            int prazoPadraoHoras, int horasSemAtualizacao) {
        List<MalaEntregue> entreguesHoje = entregues30Dias.stream().filter(m -> !m.entregueEm().isBefore(inicioHoje)).toList();
        return new PainelGestaoResponse(
                agora,
                prazoPadraoHoras,
                horasSemAtualizacao,
                radar(abertas, agora),
                new PainelGestaoResponse.Funil(
                        abertas.stream().filter(m -> m.retiradaEm() == null).count(),
                        abertas.stream().filter(m -> m.retiradaEm() != null).count(),
                        entreguesHoje.size(),
                        abertas.stream().filter(m -> m.tentativas() > 0).count()),
                new PainelGestaoResponse.Otd(otd(entreguesHoje), otd(entregues30Dias), entreguesHoje.size(), entregues30Dias.size()),
                new PainelGestaoResponse.Ciclo(cicloMedio(entreguesHoje), cicloMedio(entregues30Dias)),
                excecoes(abertas, entregues30Dias, agora, horasSemAtualizacao));
    }

    private static PainelGestaoResponse.Radar radar(List<MalaAberta> abertas, Instant agora) {
        Instant limiteAlerta = agora.plus(ALERTA);
        Predicate<MalaAberta> atrasada = m -> m.prazo() != null && m.prazo().isBefore(agora);
        Predicate<MalaAberta> alerta = m -> m.prazo() != null && !atrasada.test(m) && m.prazo().isBefore(limiteAlerta);
        return new PainelGestaoResponse.Radar(
                abertas.stream().filter(atrasada.negate().and(alerta.negate())).count(),
                lista(abertas.stream().filter(alerta), m -> item(m, m.prazo(), null)),
                lista(abertas.stream().filter(atrasada), m -> item(m, m.prazo(), null)));
    }

    private static PainelGestaoResponse.Excecoes excecoes(List<MalaAberta> abertas, List<MalaEntregue> entregues, Instant agora,
            int horasSemAtualizacao) {
        Instant limiteParada = agora.minus(Duration.ofHours(horasSemAtualizacao));
        List<MalaEntregue> comAvaria = entregues.stream().filter(m -> m.avariaRetirada() || m.avariaEntrega()).toList();
        List<MalaAberta> avariadasAbertas = abertas.stream().filter(MalaAberta::avariaRetirada).toList();
        return new PainelGestaoResponse.Excecoes(
                lista(abertas.stream().filter(m -> m.tentativas() > 0), m -> item(m, m.ultimaAtualizacao(), m.ultimoMotivo())),
                lista(abertas.stream()
                                .filter(m -> m.retiradaEm() != null || COM_MOTORISTA.contains(m.status()))
                                .filter(m -> m.ultimaAtualizacao() != null && m.ultimaAtualizacao().isBefore(limiteParada)),
                        m -> item(m, m.ultimaAtualizacao(), null)),
                listaEntregues(entregues.stream().filter(m -> evidenciaPendente(m) != null),
                        m -> item(m, m.entregueEm(), evidenciaPendente(m))),
                new PainelGestaoResponse.Avarias(
                        avariadasAbertas.size() + entregues.stream().filter(MalaEntregue::avariaRetirada).count(),
                        entregues.stream().filter(MalaEntregue::avariaEntrega).count(),
                        entregues.size(),
                        Stream.concat(
                                        avariadasAbertas.stream().map(m -> item(m, m.retiradaEm(), AVARIA_RETIRADA)),
                                        comAvaria.stream().map(m -> item(m, m.entregueEm(), tipoAvaria(m))))
                                .limit(LIMITE_LISTA)
                                .toList()));
    }

    /** Sem foto da mala na porta; ou sem prova de quem recebeu (nem foto do BDO assinado, nem nome do recebedor). */
    static String evidenciaPendente(MalaEntregue m) {
        if (!m.fotoPorta()) {
            return SEM_FOTO_PORTA;
        }
        return !m.fotoBdoAssinado() && !m.recebedor() ? SEM_ASSINATURA : null;
    }

    private static String tipoAvaria(MalaEntregue m) {
        if (m.avariaRetirada() && m.avariaEntrega()) {
            return AVARIA_AMBAS;
        }
        return m.avariaRetirada() ? AVARIA_RETIRADA : AVARIA_ENTREGA;
    }

    private static Double otd(List<MalaEntregue> entregues) {
        List<MalaEntregue> comPrazo = entregues.stream().filter(m -> m.prazo() != null).toList();
        if (comPrazo.isEmpty()) {
            return null;
        }
        long noPrazo = comPrazo.stream().filter(m -> !m.entregueEm().isAfter(m.prazo())).count();
        return Math.round(noPrazo * 1000.0 / comPrazo.size()) / 10.0;
    }

    private static Long cicloMedio(List<MalaEntregue> entregues) {
        List<Long> minutos = entregues.stream()
                .filter(m -> m.retiradaEm() != null && m.retiradaEm().isBefore(m.entregueEm()))
                .map(m -> Duration.between(m.retiradaEm(), m.entregueEm()).toMinutes())
                .toList();
        return minutos.isEmpty() ? null : Math.round(minutos.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static List<ItemMala> lista(Stream<MalaAberta> malas, Function<MalaAberta, ItemMala> item) {
        return malas.sorted(Comparator.comparing(MalaAberta::prazo, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(LIMITE_LISTA)
                .map(item)
                .toList();
    }

    private static List<ItemMala> listaEntregues(Stream<MalaEntregue> malas, Function<MalaEntregue, ItemMala> item) {
        return malas.sorted(Comparator.comparing(MalaEntregue::entregueEm).reversed()).limit(LIMITE_LISTA).map(item).toList();
    }

    private static ItemMala item(MalaAberta m, Instant referencia, String detalhe) {
        return new ItemMala(m.bdoId(), m.entregaId(), m.numero(), m.passageiro(), m.base(), m.motorista(), m.prazo(), referencia, detalhe);
    }

    private static ItemMala item(MalaEntregue m, Instant referencia, String detalhe) {
        return new ItemMala(m.bdoId(), m.entregaId(), m.numero(), m.passageiro(), m.base(), m.motorista(), m.prazo(), referencia, detalhe);
    }
}
