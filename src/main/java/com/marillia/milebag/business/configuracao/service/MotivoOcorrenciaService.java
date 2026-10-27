package com.marillia.milebag.business.configuracao.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.MotivoOcorrencia;
import com.marillia.milebag.business.configuracao.repository.MotivoOcorrenciaRepository;
import com.marillia.milebag.core.config.security.TenantContext;
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

    /** Catalogo inicial de toda empresa nova - sem ele o motorista nao consegue registrar ocorrencia (HU07). Mesma lista de V35. */
    static final List<MotivoPadrao> MOTIVOS_PADRAO = List.of(
            new MotivoPadrao("Ausência do recebedor", false),
            new MotivoPadrao("Endereço não localizado", false),
            new MotivoPadrao("Recusa de recebimento", false),
            new MotivoPadrao("Reagendada com o passageiro", true));

    record MotivoPadrao(String descricao, boolean reagendamento) {
    }

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

    /**
     * Cria o catalogo padrao no tenant indicado, se ele ainda nao tiver nenhum
     * motivo (ativo ou nao). Chamado no cadastro da empresa (HU14), fora do
     * contexto de tenant - por isso define o tenant para o RLS de
     * motivo_ocorrencia aceitar os INSERTs.
     */
    @Transactional
    public void criarPadroesSeAusentes(UUID tenantId) {
        UUID tenantAnterior = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            if (motivoOcorrenciaRepository.count() > 0) {
                return;
            }
            MOTIVOS_PADRAO.forEach(padrao -> motivoOcorrenciaRepository.save(new MotivoOcorrencia(padrao.descricao(), padrao.reagendamento())));
            log.info("Motivos de ocorrencia padrao criados para o tenant {}", tenantId);
        } finally {
            if (tenantAnterior == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(tenantAnterior);
            }
        }
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
