package com.marillia.milebag.business.configuracao.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.BaseAeroportuariaResponse;
import com.marillia.milebag.business.bagagemencontrada.service.AeroportoService;
import com.marillia.milebag.business.bagagemencontrada.model.Aeroporto;
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
    private final AeroportoService aeroportoService;

    public BaseAeroportuariaService(BaseAeroportuariaRepository baseAeroportuariaRepository, AeroportoService aeroportoService) {
        this.baseAeroportuariaRepository = baseAeroportuariaRepository;
        this.aeroportoService = aeroportoService;
    }

    /**
     * V52: o codigo tem de ser de um aeroporto do cadastro (dele vem a cidade e a posicao usadas no calculo da viagem).
     * A primeira base da empresa ja nasce principal.
     */
    @Transactional
    public BaseAeroportuaria criar(String codigoIata, String nome) {
        if (codigoIata == null || codigoIata.isBlank()) {
            throw new RegraNegocioException("codigoIata e obrigatorio.");
        }
        String codigo = codigoIata.strip().toUpperCase(Locale.ROOT);
        if (aeroportoService.buscar(codigo).isEmpty()) {
            throw new RegraNegocioException("Aeroporto " + codigo + " nao encontrado no cadastro de aeroportos.");
        }
        if (baseAeroportuariaRepository.findByCodigoIata(codigo).isPresent()) {
            throw new ConflitoException("Ja existe uma base aeroportuaria com este codigoIata.");
        }
        BaseAeroportuaria base = new BaseAeroportuaria(codigo, nome);
        boolean primeira = baseAeroportuariaRepository.count() == 0;
        base.definirPrincipal(primeira);
        BaseAeroportuaria salva;
        try {
            salva = baseAeroportuariaRepository.saveAndFlush(base);
        } catch (DataIntegrityViolationException e) {
            // Duas inclusoes ao mesmo tempo (mesmo codigo, ou duas "primeiras" bases principais).
            throw new ConflitoException("A base foi alterada por outra pessoa agora. Atualize a tela e tente de novo.");
        }
        if (primeira) {
            // Antes da primeira base, BDOs e motoristas ficaram sem: passam todos para ela.
            int bdos = baseAeroportuariaRepository.adotarBdosSemBase(salva.getId());
            int motoristas = baseAeroportuariaRepository.adotarMotoristasSemBase(salva.getId());
            log.info("Primeira base {} adotou {} BDO(s) e {} motorista(s) sem base.", codigo, bdos, motoristas);
        }
        log.info("Base aeroportuaria {} ({}) criada", salva.getId(), codigo);
        return salva;
    }

    @Transactional(readOnly = true)
    public List<BaseAeroportuaria> listar() {
        return baseAeroportuariaRepository.findAllByOrderByPrincipalDescCriadoEmAsc();
    }

    @Transactional(readOnly = true)
    public Optional<BaseAeroportuaria> buscar(UUID id) {
        return id == null ? Optional.empty() : baseAeroportuariaRepository.findById(id);
    }

    /** V52: a base de um BDO - o aeroporto de retirada dele, se for base da empresa; senao a principal. */
    @Transactional(readOnly = true)
    public Optional<BaseAeroportuaria> baseDoBdo(String aeroportoRetirada) {
        List<BaseAeroportuaria> bases = listar();
        String codigo = aeroportoRetirada == null ? "" : aeroportoRetirada.strip();
        return bases.stream()
                .filter(base -> base.getCodigoIata().equalsIgnoreCase(codigo))
                .findFirst()
                .or(() -> bases.stream().filter(BaseAeroportuaria::isPrincipal).findFirst())
                .or(() -> bases.stream().findFirst());
    }

    /** As bases com a cidade do aeroporto, para a tela de Configuracoes. */
    @Transactional(readOnly = true)
    public List<BaseAeroportuariaResponse> listarComCidade() {
        return listar().stream()
                .map(base -> new BaseAeroportuariaResponse(base.getId(), base.getCodigoIata(), base.getNome(), base.isPrincipal(),
                        aeroportoService.buscar(base.getCodigoIata()).map(Aeroporto::getCidade).orElse(null)))
                .toList();
    }

    /** V52: so uma principal por empresa - a anterior deixa de ser. */
    @Transactional
    public BaseAeroportuaria tornarPrincipal(UUID id) {
        BaseAeroportuaria escolhida = baseAeroportuariaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base aeroportuaria nao encontrada."));
        for (BaseAeroportuaria base : baseAeroportuariaRepository.findAll()) {
            if (base.isPrincipal() && !base.getId().equals(id)) {
                base.definirPrincipal(false);
                baseAeroportuariaRepository.saveAndFlush(base);
            }
        }
        escolhida.definirPrincipal(true);
        log.info("Base aeroportuaria {} ({}) passou a ser a principal", id, escolhida.getCodigoIata());
        return baseAeroportuariaRepository.save(escolhida);
    }

    /** Removendo a principal, a mais antiga das que sobram passa a ser a principal. */
    @Transactional
    public void remover(UUID id) {
        BaseAeroportuaria base = baseAeroportuariaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base aeroportuaria nao encontrada."));
        try {
            baseAeroportuariaRepository.delete(base);
            baseAeroportuariaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Esta base tem motoristas ou BDOs vinculados. Troque a base deles antes de remover.");
        }
        if (base.isPrincipal()) {
            baseAeroportuariaRepository.findAllByOrderByPrincipalDescCriadoEmAsc().stream().findFirst().ifPresent(proxima -> {
                proxima.definirPrincipal(true);
                baseAeroportuariaRepository.save(proxima);
            });
        }
        log.info("Base aeroportuaria {} removida", id);
    }
}
