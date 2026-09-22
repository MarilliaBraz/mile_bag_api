package com.marillia.milebag.business.entrega.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.StatusConferencia;
import com.marillia.milebag.business.bdo.repository.BdoRepository;
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
 * ver contracts/entrega-api.md.
 */
@Service
public class EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);

    private final EntregaRepository entregaRepository;
    private final BdoRepository bdoRepository;
    private final EntregaMapper entregaMapper;

    public EntregaService(EntregaRepository entregaRepository, BdoRepository bdoRepository, EntregaMapper entregaMapper) {
        this.entregaRepository = entregaRepository;
        this.bdoRepository = bdoRepository;
        this.entregaMapper = entregaMapper;
    }

    @Transactional
    public EntregaResponse autoatribuir(UUID bdoId, UUID motoristaId) {
        Bdo bdo = buscarBdoConfirmadoOuFalhar(bdoId);

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

    private Bdo buscarBdoConfirmadoOuFalhar(UUID bdoId) {
        return bdoRepository.findById(bdoId)
                .filter(bdo -> bdo.getStatusConferencia() == StatusConferencia.CONFIRMADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado ou ainda nao confirmado."));
    }

    private Bdo buscarBdoOuFalhar(UUID bdoId) {
        return bdoRepository.findById(bdoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
    }
}
