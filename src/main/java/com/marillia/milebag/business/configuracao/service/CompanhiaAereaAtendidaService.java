package com.marillia.milebag.business.configuracao.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.companhiaaerea.model.CompanhiaAerea;
import com.marillia.milebag.business.companhiaaerea.service.CompanhiaAereaService;
import com.marillia.milebag.business.configuracao.model.CompanhiaAereaAtendida;
import com.marillia.milebag.business.configuracao.repository.CompanhiaAereaAtendidaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Companhias aereas atendidas pelo tenant (US1 desta HU) - ver contracts/configuracoes-tenant-api.md. */
@Service
public class CompanhiaAereaAtendidaService {

    private static final Logger log = LoggerFactory.getLogger(CompanhiaAereaAtendidaService.class);

    private final CompanhiaAereaAtendidaRepository companhiaAereaAtendidaRepository;

    private final CompanhiaAereaService companhiaAereaService;

    public CompanhiaAereaAtendidaService(CompanhiaAereaAtendidaRepository companhiaAereaAtendidaRepository,
            CompanhiaAereaService companhiaAereaService) {
        this.companhiaAereaAtendidaRepository = companhiaAereaAtendidaRepository;
        this.companhiaAereaService = companhiaAereaService;
    }

    @Transactional
    public CompanhiaAereaAtendida criar(String codigoIata, String nome) {
        if (codigoIata == null || codigoIata.isBlank()) {
            throw new RegraNegocioException("codigoIata e obrigatorio.");
        }
        // V53: so companhias da lista; o codigo gravado e o IATA dela (aceita o ICAO) e o nome vem da lista se faltar.
        CompanhiaAerea daLista = companhiaAereaService.buscar(codigoIata).orElseThrow(() -> new RegraNegocioException(
                "Companhia aerea " + codigoIata.strip().toUpperCase(Locale.ROOT) + " nao esta na lista. Busque pelo nome."));
        String codigo = daLista.getCodigoIata();
        if (companhiaAereaAtendidaRepository.findByCodigoIata(codigo).isPresent()) {
            throw new ConflitoException("Ja existe uma companhia aerea atendida com este codigoIata.");
        }
        String nomeFinal = nome == null || nome.isBlank() ? daLista.getNome() : nome.strip();
        CompanhiaAereaAtendida companhia = companhiaAereaAtendidaRepository.save(new CompanhiaAereaAtendida(codigo, nomeFinal));
        log.info("Companhia aerea atendida {} ({}) criada", companhia.getId(), codigo);
        return companhia;
    }

    @Transactional(readOnly = true)
    public List<CompanhiaAereaAtendida> listar() {
        return companhiaAereaAtendidaRepository.findAll();
    }

    @Transactional
    public void remover(UUID id) {
        CompanhiaAereaAtendida companhia = companhiaAereaAtendidaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Companhia aerea atendida nao encontrada."));
        companhiaAereaAtendidaRepository.delete(companhia);
        log.info("Companhia aerea atendida {} removida", id);
    }
}
