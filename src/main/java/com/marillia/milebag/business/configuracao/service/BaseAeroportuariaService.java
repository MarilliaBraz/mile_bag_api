package com.marillia.milebag.business.configuracao.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.business.configuracao.repository.BaseAeroportuariaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Bases aeroportuarias do tenant (US1 desta HU) - ver contracts/configuracoes-tenant-api.md. */
@Service
public class BaseAeroportuariaService {

    private static final Logger log = LoggerFactory.getLogger(BaseAeroportuariaService.class);

    private final BaseAeroportuariaRepository baseAeroportuariaRepository;

    public BaseAeroportuariaService(BaseAeroportuariaRepository baseAeroportuariaRepository) {
        this.baseAeroportuariaRepository = baseAeroportuariaRepository;
    }

    @Transactional
    public BaseAeroportuaria criar(String codigoIata, String nome) {
        if (codigoIata == null || codigoIata.isBlank()) {
            throw new RegraNegocioException("codigoIata e obrigatorio.");
        }
        if (baseAeroportuariaRepository.findByCodigoIata(codigoIata).isPresent()) {
            throw new ConflitoException("Ja existe uma base aeroportuaria com este codigoIata.");
        }
        BaseAeroportuaria base = baseAeroportuariaRepository.save(new BaseAeroportuaria(codigoIata, nome));
        log.info("Base aeroportuaria {} ({}) criada", base.getId(), codigoIata);
        return base;
    }

    @Transactional(readOnly = true)
    public List<BaseAeroportuaria> listar() {
        return baseAeroportuariaRepository.findAll();
    }

    @Transactional
    public void remover(UUID id) {
        BaseAeroportuaria base = baseAeroportuariaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base aeroportuaria nao encontrada."));
        baseAeroportuariaRepository.delete(base);
        log.info("Base aeroportuaria {} removida", id);
    }
}
