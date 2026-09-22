package com.marillia.milebag.business.configuracao.service;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.PeriodicidadeAtualResponse;
import com.marillia.milebag.business.configuracao.model.PeriodicidadeFechamentoConfig;
import com.marillia.milebag.business.configuracao.model.Periodicidade;
import com.marillia.milebag.business.configuracao.repository.PeriodicidadeFechamentoConfigRepository;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Periodicidade de fechamento por tenant, versionada por vigencia (US1
 * desta HU) - ver contracts/configuracoes-tenant-api.md.
 */
@Service
public class PeriodicidadeFechamentoConfigService {

    private static final Logger log = LoggerFactory.getLogger(PeriodicidadeFechamentoConfigService.class);
    private static final Periodicidade PADRAO_SISTEMA = Periodicidade.QUINZENAL;

    private final PeriodicidadeFechamentoConfigRepository periodicidadeFechamentoConfigRepository;

    public PeriodicidadeFechamentoConfigService(PeriodicidadeFechamentoConfigRepository periodicidadeFechamentoConfigRepository) {
        this.periodicidadeFechamentoConfigRepository = periodicidadeFechamentoConfigRepository;
    }

    /**
     * FR-008: rejeitar quando a nova vigencia cai dentro do periodo de
     * fechamento ja em andamento. O calculo exato dos limites desse
     * periodo depende de HU12 (fechamento), que ainda nao existe - por
     * enquanto, a mesma regra ja usada em HU09/HU10 (vigencia sempre
     * estritamente posterior a mais recente ja configurada) evita
     * retroatividade e ambiguidade; refinar quando HU12 puder informar o
     * fim real do periodo em curso.
     */
    @Transactional
    public PeriodicidadeFechamentoConfig agendarNovaPeriodicidade(Periodicidade periodicidade, LocalDate vigenteDesde) {
        if (periodicidade == null) {
            throw new RegraNegocioException("periodicidade e obrigatoria.");
        }
        if (vigenteDesde == null) {
            throw new RegraNegocioException("vigenteDesde e obrigatorio.");
        }

        Optional<PeriodicidadeFechamentoConfig> maisRecente = periodicidadeFechamentoConfigRepository.findFirstByOrderByVigenteDesdeDesc();
        if (maisRecente.isPresent() && !vigenteDesde.isAfter(maisRecente.get().getVigenteDesde())) {
            throw new RegraNegocioException(
                    "vigenteDesde deve ser posterior ao periodo de fechamento em andamento (vigente desde "
                            + maisRecente.get().getVigenteDesde() + ").");
        }

        PeriodicidadeFechamentoConfig config =
                periodicidadeFechamentoConfigRepository.save(new PeriodicidadeFechamentoConfig(periodicidade, vigenteDesde));
        log.info("Periodicidade de fechamento agendada: {} a partir de {}", periodicidade, vigenteDesde);
        return config;
    }

    /** Usado por HU12 (fechamento) - ainda sem chamador nesta HU. Sem configuracao, o padrao do sistema e QUINZENAL. */
    @Transactional(readOnly = true)
    public Periodicidade periodicidadeVigenteEm(LocalDate data) {
        return periodicidadeFechamentoConfigRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(data)
                .map(PeriodicidadeFechamentoConfig::getPeriodicidade)
                .orElse(PADRAO_SISTEMA);
    }

    @Transactional(readOnly = true)
    public PeriodicidadeAtualResponse consultarAtual() {
        LocalDate hoje = LocalDate.now();
        Periodicidade vigente = periodicidadeVigenteEm(hoje);
        Optional<PeriodicidadeFechamentoConfig> proxima =
                periodicidadeFechamentoConfigRepository.findFirstByVigenteDesdeGreaterThanOrderByVigenteDesdeAsc(hoje);

        return new PeriodicidadeAtualResponse(
                vigente,
                proxima.map(PeriodicidadeFechamentoConfig::getPeriodicidade).orElse(null),
                proxima.map(PeriodicidadeFechamentoConfig::getVigenteDesde).orElse(null));
    }
}
