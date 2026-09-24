package com.marillia.milebag.business.entrega.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaRepository;
import com.marillia.milebag.business.entrega.mapper.EntregaMapper;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaResponse;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Autoatribuicao da entrega a partir de um BDO confirmado (US1 desta HU) -
 * ver contracts/entrega-api.md. Tambem e o unico ponto de escrita em {@code
 * Entrega} para os demais dominios (viagem, comprovante, ocorrencia): eles
 * chamam {@link #marcarEmAndamento}/{@link #marcarEncerradaComSucesso}/
 * {@link #reabrirParaNovaTentativa} em vez de mutar a entidade direto,
 * preservando o encapsulamento deste dominio (CONVENTIONS.md 2.1, SRP).
 */
@Service
public class EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);

    private final EntregaRepository entregaRepository;
    private final BdoService bdoService;
    private final EntregaMapper entregaMapper;
    private final BagagemCaronaRepository bagagemCaronaRepository;

    public EntregaService(
            EntregaRepository entregaRepository, BdoService bdoService, EntregaMapper entregaMapper,
            BagagemCaronaRepository bagagemCaronaRepository) {
        this.bagagemCaronaRepository = bagagemCaronaRepository;
        this.entregaRepository = entregaRepository;
        this.bdoService = bdoService;
        this.entregaMapper = entregaMapper;
    }

    @Transactional(readOnly = true)
    public Optional<Entrega> buscarPorId(UUID id) {
        return entregaRepository.findById(id);
    }

    @Transactional
    public void marcarEmAndamento(UUID entregaId) {
        Entrega entrega = buscarOuFalhar(entregaId);
        entrega.setStatus(EntregaStatus.EM_ANDAMENTO);
        entregaRepository.save(entrega);
    }

    @Transactional
    public void marcarEncerradaComSucesso(UUID entregaId) {
        Entrega entrega = buscarOuFalhar(entregaId);
        entrega.setStatus(EntregaStatus.ENCERRADA_COM_SUCESSO);
        entregaRepository.save(entrega);
    }

    @Transactional
    public void reabrirParaNovaTentativa(UUID entregaId) {
        Entrega entrega = buscarOuFalhar(entregaId);
        entrega.setStatus(EntregaStatus.REABERTA);
        entrega.setMotoristaResponsavel(null);
        entregaRepository.save(entrega);
    }

    @Transactional
    public EntregaResponse autoatribuir(UUID bdoId, UUID motoristaId) {
        Bdo bdo = buscarBdoConfirmadoOuFalhar(bdoId);
        // Um BDO ja vinculado como bagagem carona viaja junto de outra entrega: nao pode virar entrega propria.
        if (bagagemCaronaRepository.findByBdoId(bdoId).isPresent()) {
            throw new ConflitoException("Este BDO ja esta vinculado como bagagem carona de outra entrega.");
        }

        int atribuidas = entregaRepository.autoatribuirSeDisponivel(
                UUID.randomUUID(), TenantContext.get(), bdoId, motoristaId);
        if (atribuidas == 0) {
            throw new ConflitoException("Esta entrega ja possui um motorista responsavel.");
        }

        Entrega entrega = entregaRepository.findByBdoId(bdoId)
                .orElseThrow(() -> new IllegalStateException(
                        "Entrega do BDO " + bdoId + " deveria existir apos atribuicao bem sucedida."));

        log.info("Entrega {} autoatribuida ao motorista {} (BDO {})", entrega.getId(), motoristaId, bdoId);
        return entregaMapper.toResponse(entrega, bdo);
    }

    @Transactional(readOnly = true)
    public List<EntregaResponse> listarPorMotorista(UUID motoristaId, EntregaStatus status) {
        return entregaRepository.findByMotoristaResponsavelAndStatus(motoristaId, status).stream()
                .map(entrega -> entregaMapper.toResponse(entrega, buscarBdoOuFalhar(entrega.getBdoId())))
                .toList();
    }

    private Entrega buscarOuFalhar(UUID entregaId) {
        return entregaRepository.findById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
    }

    private Bdo buscarBdoConfirmadoOuFalhar(UUID bdoId) {
        return bdoService.buscarPorId(bdoId)
                .filter(bdo -> bdo.getStatusConferencia() == StatusConferencia.CONFIRMADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado ou ainda nao confirmado."));
    }

    private Bdo buscarBdoOuFalhar(UUID bdoId) {
        return bdoService.buscarPorId(bdoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
    }
}
