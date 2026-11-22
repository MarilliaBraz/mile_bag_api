package com.marillia.milebag.business.viagem.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.viagem.model.PosicaoViagem;
import com.marillia.milebag.business.viagem.model.RegistrarPosicoesViagemRequest;
import com.marillia.milebag.business.viagem.model.RegistrarPosicoesViagemRequest.Posicao;
import com.marillia.milebag.business.viagem.model.RegistrarPosicoesViagemResponse;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.repository.PosicaoViagemRepository;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.geo.Coordenada;
import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.RoutingClient;

/**
 * Item 9: recebe os pontos de GPS gravados pelo app durante a viagem - em lotes, tambem os guardados sem internet - e
 * recalcula o km percorrido de cada viagem tocada ({@link CalculadoraKmPercorrido}).
 *
 * <p>Cada ponto vai para a viagem da entrega dele que estava em andamento na hora em que foi lido. Se nenhuma estava, e a
 * viagem iniciada sem internet - no servidor ela so comeca quando sincroniza: o ponto vai para a primeira viagem que
 * comecou depois dele (ate {@link #FOLGA_ANTES_DO_INICIO} antes), nunca para uma tentativa anterior. Ponto sem viagem ainda
 * volta como pendente (o app reenvia depois). Ponto de viagem de outro motorista nunca entra.</p>
 *
 * <p>As viagens da entrega ficam travadas durante o lote: lotes simultaneos entram um de cada vez, e o km recalculado por
 * um inclui os pontos do outro.</p>
 */
@Service
public class PosicaoViagemService {

    private static final Logger log = LoggerFactory.getLogger(PosicaoViagemService.class);

    public static final int LOTE_MAXIMO = 500;
    static final Duration FOLGA_ANTES_DO_INICIO = Duration.ofHours(24);
    static final Duration FOLGA_DEPOIS_DO_FIM = Duration.ofMinutes(10);
    private static final Duration FUTURO_MAXIMO = Duration.ofMinutes(5);
    private static final int TAMANHO_MAXIMO_ID_LOCAL = 64;

    private final PosicaoViagemRepository posicaoViagemRepository;
    private final TrajetoRepository trajetoRepository;
    private final RoutingClient routingClient;
    /** Distancias pela estrada ja consultadas (limitado: as mais antigas saem primeiro). */
    private final Map<String, Double> distanciasPelaEstrada = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Double> maisAntiga) {
                    return size() > DISTANCIAS_GUARDADAS;
                }
            });
    private static final int DISTANCIAS_GUARDADAS = 5_000;

    public PosicaoViagemService(PosicaoViagemRepository posicaoViagemRepository, TrajetoRepository trajetoRepository,
            RoutingClient routingClient) {
        this.posicaoViagemRepository = posicaoViagemRepository;
        this.trajetoRepository = trajetoRepository;
        this.routingClient = routingClient;
    }

    @Transactional
    public RegistrarPosicoesViagemResponse registrar(UUID motoristaId, RegistrarPosicoesViagemRequest request) {
        if (request == null || request.posicoes() == null) {
            throw new RegraNegocioException("Informe as posicoes da viagem.");
        }
        if (request.posicoes().size() > LOTE_MAXIMO) {
            throw new RegraNegocioException("Envie no maximo " + LOTE_MAXIMO + " posicoes por vez.");
        }
        Instant limiteFuturo = Instant.now().plus(FUTURO_MAXIMO);
        List<Posicao> validas = request.posicoes().stream().filter(p -> valida(p, limiteFuturo)).toList();
        // Trava as viagens antes de olhar os pontos ja registrados: um lote simultaneo espera este terminar.
        Map<UUID, List<Trajeto>> viagensPorEntrega = new HashMap<>();
        validas.stream().map(Posicao::entregaId).distinct()
                .forEach(entrega -> viagensPorEntrega.put(entrega, viagensDoMotorista(entrega, motoristaId)));
        Set<String> jaRegistrados = validas.isEmpty() ? Set.of()
                : new HashSet<>(posicaoViagemRepository.idsLocaisJaRegistrados(validas.stream().map(Posicao::idLocal).toList()));

        Set<String> vistos = new HashSet<>(jaRegistrados);
        Map<UUID, Trajeto> tocadas = new HashMap<>();
        List<String> pendentes = new ArrayList<>();
        int aceitas = 0;
        for (Posicao posicao : validas) {
            if (!vistos.add(posicao.idLocal())) {
                continue;
            }
            Optional<Trajeto> viagem = viagemDaHora(viagensPorEntrega.get(posicao.entregaId()), posicao.dataHora());
            if (viagem.isEmpty()) {
                pendentes.add(posicao.idLocal());
                continue;
            }
            posicaoViagemRepository.save(new PosicaoViagem(viagem.get().getId(), posicao.latitude(), posicao.longitude(),
                    posicao.precisaoMetros(), posicao.dataHora(), posicao.idLocal()));
            tocadas.put(viagem.get().getId(), viagem.get());
            aceitas++;
        }
        tocadas.values().forEach(this::recalcularKm);
        log.info("Posicoes da viagem do motorista {}: {} aceita(s), {} pendente(s), {} viagem(ns) recalculada(s)",
                motoristaId, aceitas, pendentes.size(), tocadas.size());
        return new RegistrarPosicoesViagemResponse(aceitas, pendentes);
    }

    private static boolean valida(Posicao p, Instant limiteFuturo) {
        return p != null && p.idLocal() != null && !p.idLocal().isBlank() && p.idLocal().length() <= TAMANHO_MAXIMO_ID_LOCAL
                && p.entregaId() != null && p.dataHora() != null && !p.dataHora().isAfter(limiteFuturo)
                && p.latitude() != null && p.longitude() != null
                && Math.abs(p.latitude()) <= 90 && Math.abs(p.longitude()) <= 180;
    }

    private List<Trajeto> viagensDoMotorista(UUID entregaId, UUID motoristaId) {
        return trajetoRepository.findByEntregaIdParaAtualizar(entregaId).stream()
                .filter(trajeto -> motoristaId.equals(trajeto.getMotoristaId()))
                .toList();
    }

    /**
     * A viagem em andamento na hora do ponto; senao (viagem iniciada sem internet) a primeira que comecou depois dele -
     * desde que o ponto seja posterior ao fim da tentativa anterior e esteja dentro da folga.
     */
    private static Optional<Trajeto> viagemDaHora(List<Trajeto> viagens, Instant hora) {
        Optional<Trajeto> emAndamento = viagens.stream()
                .filter(v -> !hora.isBefore(v.getDataInicio()))
                .filter(v -> v.getEncerradoEm() == null || !hora.isAfter(v.getEncerradoEm().plus(FOLGA_DEPOIS_DO_FIM)))
                .max(Comparator.comparing(Trajeto::getDataInicio));
        if (emAndamento.isPresent()) {
            return emAndamento;
        }
        Optional<Trajeto> seguinte = viagens.stream()
                .filter(v -> hora.isBefore(v.getDataInicio()))
                .min(Comparator.comparing(Trajeto::getDataInicio));
        if (seguinte.isEmpty() || hora.isBefore(seguinte.get().getDataInicio().minus(FOLGA_ANTES_DO_INICIO))) {
            return Optional.empty();
        }
        boolean depoisDaAnterior = viagens.stream()
                .filter(v -> v.getDataInicio().isBefore(seguinte.get().getDataInicio()))
                .allMatch(v -> v.getEncerradoEm() != null && hora.isAfter(v.getEncerradoEm()));
        return depoisDaAnterior ? seguinte : Optional.empty();
    }

    private void recalcularKm(Trajeto viagem) {
        List<CalculadoraKmPercorrido.Ponto> pontos = posicaoViagemRepository.findByTrajetoIdOrderByDataHoraAsc(viagem.getId()).stream()
                .map(p -> new CalculadoraKmPercorrido.Ponto(p.getLatitude(), p.getLongitude(), p.getPrecisaoMetros(), p.getDataHora()))
                .toList();
        viagem.registrarKmPercorrido(CalculadoraKmPercorrido.calcular(pontos, this::distanciaPelaEstrada));
        trajetoRepository.save(viagem);
    }

    /**
     * A menor alternativa do motor de rotas; vazio se ele nao responder (a calculadora usa a linha reta). Guardada em
     * memoria: o km e recalculado a cada lote, e as lacunas antigas da viagem nao precisam ir ao motor de novo.
     */
    private OptionalDouble distanciaPelaEstrada(Coordenada de, Coordenada para) {
        String chave = de.latitude() + "," + de.longitude() + ">" + para.latitude() + "," + para.longitude();
        Double guardada = distanciasPelaEstrada.get(chave);
        if (guardada != null) {
            return OptionalDouble.of(guardada);
        }
        try {
            OptionalDouble metros = routingClient.calcularAlternativas(de, para).stream().mapToDouble(RotaAlternativa::distanciaMetros).min();
            metros.ifPresent(valor -> distanciasPelaEstrada.put(chave, valor));
            return metros;
        } catch (RuntimeException e) {
            log.warn("Motor de rotas indisponivel para completar um trecho da viagem; vale a linha reta.", e);
            return OptionalDouble.empty();
        }
    }
}
