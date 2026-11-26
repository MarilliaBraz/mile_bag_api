package com.marillia.milebag.business.painelgestao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import com.marillia.milebag.business.painelgestao.model.MotoristaPeriodo;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.Custo;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.MotoristaStatus;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.Ocupacao;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.Praca;
import com.marillia.milebag.business.painelgestao.model.PainelFrotaResponse.SituacaoMotorista;
import com.marillia.milebag.business.painelgestao.model.ViagemPeriodo;

/** Regras da fase 3 (custo, ocupacao e motoristas) sobre o que ja foi lido: puro, sem banco. */
final class CalculoPainelFrota {

    /** Usou o app ha menos que isto: online (disponivel, se nao estiver em rota). */
    static final Duration ONLINE = Duration.ofMinutes(15);

    private CalculoPainelFrota() {
    }

    static PainelFrotaResponse calcular(List<ViagemPeriodo> viagens, List<MotoristaPeriodo> motoristas, Instant agora, Instant inicioHoje,
            Instant inicio30Dias) {
        List<ViagemPeriodo> saidas30 = viagens.stream().filter(v -> v.inicio() != null && !v.inicio().isBefore(inicio30Dias)).toList();
        Map<UUID, List<ViagemPeriodo>> saidasPorMotorista = saidas30.stream()
                .filter(v -> v.motoristaId() != null)
                .collect(Collectors.groupingBy(ViagemPeriodo::motoristaId));
        List<MotoristaStatus> status = motoristas.stream()
                .map(m -> motorista(m, saidasPorMotorista.getOrDefault(m.id(), List.of()), agora))
                .sorted(Comparator.comparing(MotoristaStatus::pontualidade, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MotoristaStatus::entregas, Comparator.reverseOrder()))
                .toList();
        return new PainelFrotaResponse(agora, custo(viagens, inicioHoje), custo(viagens, inicio30Dias), ocupacao(saidas30), pracas(status),
                status);
    }

    private static Custo custo(List<ViagemPeriodo> viagens, Instant desde) {
        List<ViagemPeriodo> encerradas = viagens.stream().filter(v -> v.encerradaEm() != null && !v.encerradaEm().isBefore(desde)).toList();
        List<ViagemPeriodo> comValor = encerradas.stream().filter(v -> v.pagamento() != null && v.pedagio() != null).toList();
        BigDecimal pagamento = somar(comValor.stream().map(ViagemPeriodo::pagamento).toList());
        BigDecimal pedagios = somar(comValor.stream().map(ViagemPeriodo::pedagio).toList());
        BigDecimal total = pagamento.add(pedagios);
        long malas = encerradas.stream().filter(ViagemPeriodo::entregue).mapToLong(ViagemPeriodo::malas).sum();
        double km = Math.round(encerradas.stream().mapToDouble(ViagemPeriodo::km).sum() * 10) / 10.0;
        return new Custo(total, pagamento, pedagios, encerradas.size(), encerradas.size() - comValor.size(), malas,
                malas == 0 ? null : total.divide(BigDecimal.valueOf(malas), 2, RoundingMode.HALF_UP),
                km, km == 0 ? null : total.divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP));
    }

    private static Ocupacao ocupacao(List<ViagemPeriodo> saidas) {
        return new Ocupacao(saidas.size(), malasPorSaida(saidas), saidas.stream().filter(v -> v.malas() <= 1).count());
    }

    private static Double malasPorSaida(List<ViagemPeriodo> saidas) {
        return saidas.isEmpty() ? null : Math.round(saidas.stream().mapToLong(ViagemPeriodo::malas).average().orElse(0) * 10) / 10.0;
    }

    private static MotoristaStatus motorista(MotoristaPeriodo m, List<ViagemPeriodo> saidas, Instant agora) {
        SituacaoMotorista situacao = m.emRota() ? SituacaoMotorista.EM_ROTA
                : m.ultimoAcesso() != null && m.ultimoAcesso().isAfter(agora.minus(ONLINE)) ? SituacaoMotorista.DISPONIVEL
                : SituacaoMotorista.OFFLINE;
        return new MotoristaStatus(m.id(), m.nome(), m.base(), situacao, m.ultimoAcesso(), m.entregas(), percentual(m.noPrazo(), m.entregas()),
                percentual(m.primeiraTentativa(), m.entregas()), malasPorSaida(saidas));
    }

    /** Uma linha por praca (base do motorista), com quantos estao em rota, disponiveis e offline. */
    private static List<Praca> pracas(List<MotoristaStatus> motoristas) {
        return motoristas.stream()
                .collect(Collectors.groupingBy(m -> Objects.requireNonNullElse(m.base(), ""), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(praca -> new Praca(praca.getKey(), contar(praca.getValue(), SituacaoMotorista.EM_ROTA),
                        contar(praca.getValue(), SituacaoMotorista.DISPONIVEL), contar(praca.getValue(), SituacaoMotorista.OFFLINE)))
                .toList();
    }

    private static long contar(List<MotoristaStatus> motoristas, SituacaoMotorista situacao) {
        return motoristas.stream().filter(m -> m.situacao() == situacao).count();
    }

    private static Double percentual(long parte, long total) {
        return total == 0 ? null : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private static BigDecimal somar(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
