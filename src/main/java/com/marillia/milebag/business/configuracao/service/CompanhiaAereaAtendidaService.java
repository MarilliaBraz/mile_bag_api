package com.marillia.milebag.business.configuracao.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public CompanhiaAereaAtendidaService(CompanhiaAereaAtendidaRepository companhiaAereaAtendidaRepository) {
        this.companhiaAereaAtendidaRepository = companhiaAereaAtendidaRepository;
    }

    @Transactional
    public CompanhiaAereaAtendida criar(String codigoIata, String nome) {
        if (codigoIata == null || codigoIata.isBlank()) {
            throw new RegraNegocioException("codigoIata e obrigatorio.");
        }
        if (companhiaAereaAtendidaRepository.findByCodigoIata(codigoIata).isPresent()) {
            throw new ConflitoException("Ja existe uma companhia aerea atendida com este codigoIata.");
        }
        CompanhiaAereaAtendida companhia = companhiaAereaAtendidaRepository.save(new CompanhiaAereaAtendida(codigoIata, nome));
        log.info("Companhia aerea atendida {} ({}) criada", companhia.getId(), codigoIata);
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
