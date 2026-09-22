package com.marillia.milebag.business.ocorrencia.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.business.ocorrencia.model.MotivoOcorrencia;
import com.marillia.milebag.business.ocorrencia.model.Ocorrencia;
import com.marillia.milebag.business.ocorrencia.model.OcorrenciaResponse;
import com.marillia.milebag.business.ocorrencia.repository.MotivoOcorrenciaRepository;
import com.marillia.milebag.business.ocorrencia.repository.OcorrenciaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Registra ocorrencias de nao entrega e reabre a entrega para nova
 * tentativa (US1 desta HU) - ver contracts/ocorrencia-api.md.
 */
@Service
public class OcorrenciaService {

    private static final Logger log = LoggerFactory.getLogger(OcorrenciaService.class);

    private final OcorrenciaRepository ocorrenciaRepository;
    private final MotivoOcorrenciaRepository motivoOcorrenciaRepository;
    private final EntregaRepository entregaRepository;

    public OcorrenciaService(
            OcorrenciaRepository ocorrenciaRepository,
            MotivoOcorrenciaRepository motivoOcorrenciaRepository,
            EntregaRepository entregaRepository) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.motivoOcorrenciaRepository = motivoOcorrenciaRepository;
        this.entregaRepository = entregaRepository;
    }

    @Transactional
    public OcorrenciaResponse registrar(UUID entregaId, UUID motivoOcorrenciaId, String descricaoAdicional, String idLocal) {
        Entrega entrega = buscarEntregaOuFalhar(entregaId);
        if (entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("Esta entrega ja foi encerrada com comprovante de entrega.");
        }

        boolean temIdLocal = idLocal != null && !idLocal.isBlank();
        if (temIdLocal) {
            Optional<Ocorrencia> existente = ocorrenciaRepository.findByEntregaIdAndIdLocal(entregaId, idLocal);
            if (existente.isPresent()) {
                log.info("Ocorrencia da entrega {} ja registrada com o mesmo idLocal - retorno idempotente.", entregaId);
                return paraResponse(existente.get(), entrega.getStatus());
            }
        }

        // RLS (motivo_ocorrencia) ja garante que um motivo de outro tenant e
        // invisivel aqui - findById retorna vazio tanto para "nao existe"
        // quanto para "existe, mas e de outro tenant" (nunca revelado como
        // tal, mesma convencao do resto da API).
        MotivoOcorrencia motivo = motivoOcorrenciaRepository.findById(motivoOcorrenciaId)
                .filter(MotivoOcorrencia::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo de ocorrencia nao encontrado."));

        Ocorrencia ocorrencia = ocorrenciaRepository.save(
                new Ocorrencia(entregaId, motivo.getId(), descricaoAdicional, Instant.now(), temIdLocal, idLocal));

        entrega.setStatus(EntregaStatus.REABERTA);
        entrega.setMotoristaResponsavel(null);
        entregaRepository.save(entrega);

        log.info(
                "Ocorrencia {} registrada para a entrega {} (motivo {}); entrega reaberta para nova tentativa.",
                ocorrencia.getId(), entregaId, motivo.getId());
        return paraResponse(ocorrencia, EntregaStatus.REABERTA);
    }

    @Transactional(readOnly = true)
    public List<OcorrenciaResponse> listarPorEntrega(UUID entregaId) {
        Entrega entrega = buscarEntregaOuFalhar(entregaId);
        return ocorrenciaRepository.findByEntregaIdOrderByDataHoraAsc(entregaId).stream()
                .map(ocorrencia -> paraResponse(ocorrencia, entrega.getStatus()))
                .toList();
    }

    private Entrega buscarEntregaOuFalhar(UUID entregaId) {
        return entregaRepository.findById(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
    }

    private OcorrenciaResponse paraResponse(Ocorrencia ocorrencia, EntregaStatus statusAtual) {
        return new OcorrenciaResponse(
                ocorrencia.getId(), ocorrencia.getEntregaId(), ocorrencia.getMotivoOcorrenciaId(), ocorrencia.getDataHora(), statusAtual);
    }
}
