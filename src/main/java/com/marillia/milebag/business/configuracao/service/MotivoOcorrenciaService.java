package com.marillia.milebag.business.configuracao.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.FinalidadeMotivo;
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

    /**
     * Catalogo inicial de toda empresa nova - sem ele o motorista nao consegue registrar ocorrencia (HU07) nem
     * justificar uma dispensa, nem a empresa cancelar um BDO. Mesmas listas de V35, V46 (dispensa) e V47 (cancelamento).
     */
    static final List<MotivoPadrao> MOTIVOS_PADRAO = List.of(
            new MotivoPadrao("Ausência do recebedor", false, FinalidadeMotivo.OCORRENCIA),
            new MotivoPadrao("Endereço não localizado", false, FinalidadeMotivo.OCORRENCIA),
            new MotivoPadrao("Recusa de recebimento", false, FinalidadeMotivo.OCORRENCIA),
            new MotivoPadrao("Reagendada com o passageiro", true, FinalidadeMotivo.OCORRENCIA),
            new MotivoPadrao("Problema no veículo", false, FinalidadeMotivo.DISPENSA),
            new MotivoPadrao("Imprevisto pessoal", false, FinalidadeMotivo.DISPENSA),
            new MotivoPadrao("Indisponibilidade de horário", false, FinalidadeMotivo.DISPENSA),
            new MotivoPadrao("Outro motivo", false, FinalidadeMotivo.DISPENSA),
            new MotivoPadrao("Enviado por engano", false, FinalidadeMotivo.CANCELAMENTO),
            new MotivoPadrao("Cliente retirou a bagagem no aeroporto", false, FinalidadeMotivo.CANCELAMENTO),
            new MotivoPadrao("BDO duplicado", false, FinalidadeMotivo.CANCELAMENTO),
            new MotivoPadrao("Outro motivo", false, FinalidadeMotivo.CANCELAMENTO));

    record MotivoPadrao(String descricao, boolean reagendamento, FinalidadeMotivo finalidade) {
    }

    private final MotivoOcorrenciaRepository motivoOcorrenciaRepository;

    public MotivoOcorrenciaService(MotivoOcorrenciaRepository motivoOcorrenciaRepository) {
        this.motivoOcorrenciaRepository = motivoOcorrenciaRepository;
    }

    @Transactional
    public MotivoOcorrencia criar(String descricao, boolean reagendamento, FinalidadeMotivo finalidade) {
        if (descricao == null || descricao.isBlank()) {
            throw new RegraNegocioException("descricao e obrigatoria.");
        }
        if (reagendamento && finalidade != FinalidadeMotivo.OCORRENCIA) {
            throw new RegraNegocioException("Reagendamento so existe em motivo de ocorrencia.");
        }
        MotivoOcorrencia motivo = motivoOcorrenciaRepository.save(new MotivoOcorrencia(descricao, reagendamento, finalidade));
        log.info("Motivo {} de {} criado", motivo.getId(), finalidade);
        return motivo;
    }

    /**
     * Cria o catalogo padrao no tenant indicado, por finalidade: so a finalidade que ainda nao tiver nenhum motivo
     * (ativo ou nao) recebe os seus. Chamado no cadastro da empresa (HU14), fora do contexto de tenant - por isso
     * define o tenant para o RLS de motivo_ocorrencia aceitar os INSERTs.
     */
    @Transactional
    public void criarPadroesSeAusentes(UUID tenantId) {
        UUID tenantAnterior = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            for (FinalidadeMotivo finalidade : FinalidadeMotivo.values()) {
                if (motivoOcorrenciaRepository.existsByFinalidade(finalidade)) {
                    continue;
                }
                MOTIVOS_PADRAO.stream()
                        .filter(padrao -> padrao.finalidade() == finalidade)
                        .forEach(padrao -> motivoOcorrenciaRepository.save(
                                new MotivoOcorrencia(padrao.descricao(), padrao.reagendamento(), padrao.finalidade())));
                log.info("Motivos padrao de {} criados para o tenant {}", finalidade, tenantId);
            }
        } finally {
            if (tenantAnterior == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(tenantAnterior);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<MotivoOcorrencia> listarAtivos(FinalidadeMotivo finalidade) {
        return motivoOcorrenciaRepository.findByAtivoTrueAndFinalidade(finalidade);
    }

    /**
     * Ponto de leitura publico para outros dominios (ocorrencia, dispensa) - nunca {@code MotivoOcorrenciaRepository}
     * direto (CONVENTIONS.md 2.1). Um motivo de outra finalidade e tratado como inexistente: a ocorrencia nao aceita
     * motivo de dispensa, e vice-versa.
     */
    @Transactional(readOnly = true)
    public MotivoOcorrencia buscarAtivoOuFalhar(UUID id, FinalidadeMotivo finalidade) {
        return motivoOcorrenciaRepository.findById(id)
                .filter(MotivoOcorrencia::isAtivo)
                .filter(motivo -> motivo.getFinalidade() == finalidade)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo nao encontrado."));
    }

    /** Descricao para historicos: um motivo desativado depois do registro continua aparecendo com o nome que tinha. */
    @Transactional(readOnly = true)
    public Optional<String> descricao(UUID id) {
        return motivoOcorrenciaRepository.findById(id).map(MotivoOcorrencia::getDescricao);
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
