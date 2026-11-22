package com.marillia.milebag.business.companhiaaerea.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.companhiaaerea.model.CompanhiaAerea;
import com.marillia.milebag.business.companhiaaerea.repository.CompanhiaAereaRepository;

/** Lista de companhias aereas (V53): pequena e fixa em execucao, carregada uma vez e mantida em memoria. */
@Service
public class CompanhiaAereaService {

    private final CompanhiaAereaRepository companhiaAereaRepository;
    private volatile List<CompanhiaAerea> companhias;

    public CompanhiaAereaService(CompanhiaAereaRepository companhiaAereaRepository) {
        this.companhiaAereaRepository = companhiaAereaRepository;
    }

    @Transactional(readOnly = true)
    public List<CompanhiaAerea> listar() {
        List<CompanhiaAerea> carregadas = companhias;
        if (carregadas == null) {
            carregadas = List.copyOf(companhiaAereaRepository.findAllByOrderByNomeAsc());
            companhias = carregadas;
        }
        return carregadas;
    }

    /** Pelo codigo IATA (2) ou ICAO (3), sem diferenciar maiusculas. */
    public Optional<CompanhiaAerea> buscar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String c = codigo.strip().toUpperCase(Locale.ROOT);
        return listar().stream().filter(cia -> cia.getCodigoIata().equals(c) || cia.getCodigoIcao().equals(c)).findFirst();
    }
}
