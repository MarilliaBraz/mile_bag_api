package com.marillia.milebag.business.configuracao.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.repository.MotivoOcorrenciaRepository;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Catalogo de motivos de ocorrencia por tenant (US1 desta HU, sub-area
 * "Motivos de ocorrencia") - ver contracts/configuracoes-tenant-api.md.
 * CRUD completo desta entidade (HU07 so a consumia, leitura apenas).
 */
@Service
public class MotivoOcorrenciaService {

    private static final Logger log = LoggerFactory.getLogger(MotivoOcorrenciaService.class);

    private final MotivoOcorrenciaRepository motivoOcorrenciaRepository;

    public MotivoOcorrenciaService(MotivoOcorrenciaRepository motivoOcorrenciaRepository) {
        this.motivoOcorrenciaRepository = motivoOcorrenciaRepository;
    }

    @Transactional
    public MotivoOcorrencia criar(String descricao) {
        return criar(descricao, false);
    }

    @Transactional
    public MotivoOcorrencia criar(String descricao, boolean reagendamento) {
        if (descricao == null || descricao.isBlank()) {
            throw new RegraNegocioException("descricao e obrigatoria.");
        }
        MotivoOcorrencia motivo = motivoOcorrenciaRepository.save(new MotivoOcorrencia(descricao, reagendamento));
        log.info("Motivo de ocorrencia {} criado", motivo.getId());
        return motivo;
    }

    @Transactional(readOnly = true)
    public List<MotivoOcorrencia> listarAtivos() {
        return motivoOcorrenciaRepository.findByAtivoTrue();
    }

    /** Ponto de leitura publico para outros dominios (ocorrencia) - nunca {@code MotivoOcorrenciaRepository} direto (CONVENTIONS.md 2.1). */
    @Transactional(readOnly = true)
    public MotivoOcorrencia buscarAtivoOuFalhar(UUID id) {
        return motivoOcorrenciaRepository.findById(id)
                .filter(MotivoOcorrencia::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo de ocorrencia nao encontrado."));
    }

    @Transactional
    public MotivoOcorrencia atualizarAtivo(UUID id, boolean ativo) {
        MotivoOcorrencia motivo = motivoOcorrenciaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo de ocorrencia nao encontrado."));
        // FR-009: nunca excluido, so desativado/reativado - ocorrencias ja
        // registradas continuam referenciando este id normalmente.
        motivo.definirAtivo(ativo);
        MotivoOcorrencia salvo = motivoOcorrenciaRepository.save(motivo);
        log.info("Motivo de ocorrencia {} marcado como ativo={}", id, ativo);
        return salvo;
    }
}
