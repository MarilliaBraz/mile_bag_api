package com.marillia.milebag.business.bagagemencontrada.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
import com.marillia.milebag.business.bagagemencontrada.repository.AeroportoRepository;

/**
 * Aeroportos brasileiros (V41) e o mais proximo de uma posicao (HU34). A tabela e pequena (pouco mais de cem linhas) e
 * nao muda em execucao: carregada uma vez e mantida em memoria.
 */
@Service
public class AeroportoService {

    private static final double RAIO_TERRA_METROS = 6_371_000;

    private final AeroportoRepository aeroportoRepository;
    private volatile List<Aeroporto> aeroportos;

    public AeroportoService(AeroportoRepository aeroportoRepository) {
        this.aeroportoRepository = aeroportoRepository;
    }

    /** Aeroporto e a distancia ate ele, em metros. */
    public record AeroportoProximo(Aeroporto aeroporto, int distanciaMetros) {
    }

    @Transactional(readOnly = true)
    public List<Aeroporto> listar() {
        List<Aeroporto> carregados = aeroportos;
        if (carregados == null) {
            carregados = List.copyOf(aeroportoRepository.findAllByOrderByCodigoAsc());
            aeroportos = carregados;
        }
        return carregados;
    }

    public Set<String> codigos() {
        return listar().stream().map(Aeroporto::getCodigo).collect(Collectors.toUnmodifiableSet());
    }

    public Optional<Aeroporto> buscar(String codigo) {
        return codigo == null ? Optional.empty() : listar().stream().filter(a -> a.getCodigo().equalsIgnoreCase(codigo)).findFirst();
    }

    public Optional<AeroportoProximo> maisProximo(double latitude, double longitude) {
        return listar().stream()
                .map(a -> new AeroportoProximo(a, (int) Math.round(distanciaMetros(latitude, longitude, a.getLatitude(), a.getLongitude()))))
                .min(Comparator.comparingInt(AeroportoProximo::distanciaMetros));
    }

    /** Distancia geodesica pela formula de haversine - precisa o bastante para escolher o aeroporto mais perto. */
    static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RAIO_TERRA_METROS * Math.asin(Math.sqrt(a));
    }
}
